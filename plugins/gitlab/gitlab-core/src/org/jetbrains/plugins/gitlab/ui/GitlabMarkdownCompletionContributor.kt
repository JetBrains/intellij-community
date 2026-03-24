// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.onFailure
import com.intellij.collaboration.util.onSuccess
import com.intellij.collaboration.util.consumeIncrementally
import com.intellij.icons.AllIcons.General.Groups
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.patterns.StandardPatterns
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.Icon


private val LOG: Logger = logger<GitlabMarkdownCompletionContributor>()

private const val AUTHOR_PRIORITY = 15.0
private const val PARTICIPANT_PRIORITY = 10.0
private const val DEFAULT_PRIORITY = 1.0

internal class GitlabMarkdownCompletionContributor : CompletionContributor(), DumbAware {

  /**
   * Completion contributor for mentions in GitLab comments.
   *
   * In web UI they show a combination of users and groups:
   * The suggestion list contains distinct and filtered values of up to 10 entries:
   * - MR author
   * - Other participants of this MR
   * - @All with the number of project users (optional, if not hidden via project settings,
   * for admin only: /api/v4/projects/:id/feature_flags/disable_all_mention)
   * - Other members of the project
   * - Groups and subgroups with the number of affected users (optional, if filtered Project members count <= 10)
   *
   * See https://gitlab.com/gitlab-org/gitlab-foss/-/blob/master/app/services/projects/participants_service.rb
   * In GitLab web UI they sort the resulting list alphabetically.
   *
   * In our completion we can't show @all, because we can't get a feature flag.
   * Also, it's not clear how to show the number of affected users.
   * Since we show more than 10 entries, the participant will be prioritized to make them more visible
   */
  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    val vmCompletion = parameters.editor.getUserData(GitLabViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY) ?: return
    val text = parameters.position.text.substringBefore(DUMMY_IDENTIFIER_TRIMMED).takeLastWhile { !it.isWhitespace() }

    if (!text.startsWith("@")) return
    vmCompletion.withMentionCompletionModel { vm ->

      val customResult = result.withPrefixMatcher(text).caseInsensitive()
      // search endpoints start to search new items for a search string longer than 2 (@ not included),
      // so no need to restart completion if it's shorter
      result.restartCompletionOnPrefixChange(StandardPatterns.string().longerThan(2))
      customResult.addLookupAdvertisement(GitLabBundle.message("popup.completion.advertisement.type.more.for.more.suggestions"))

      runBlockingCancellable {
        val userType = GitLabBundle.message("popup.completion.type.user")
        launch {
          val author = vm.author
          vm.participants.consumeIncrementally(
            batchConsumer = { users ->
              val newElements = users.map { user ->
                val priority = if (user.username == vm.author.username) AUTHOR_PRIORITY else PARTICIPANT_PRIORITY
                val icon = vm.avatarIconsProvider.getIcon(user, Avatar.Sizes.SMALL)
                createLookupElement(user, user.username, icon, user.name, userType, priority)
              }
              customResult.addAllElements(newElements)
            },
            onError = {
              val icon = vm.avatarIconsProvider.getIcon(author, Avatar.Sizes.SMALL)
              customResult.addElement(createLookupElement(author, author.username, icon, author.name, userType, AUTHOR_PRIORITY))
              LOG.error("Error fetching GitLab users for completion", it)
            },
          )
        }

        val searchPrefix = text.substring(1)
        vm.setSearchPrefix(searchPrefix)

        launch {
          vm.foundProjectUsers.collectUntilCompleted("Error fetching GitLab project users for completion") { users ->
            customResult.addAllElements(users.map {
              val icon = vm.avatarIconsProvider.getIcon(it, Avatar.Sizes.SMALL)
              createLookupElement(it, it.username, icon, it.name, userType, DEFAULT_PRIORITY)
            })
          }
        }
        launch {
          val groupType = GitLabBundle.message("popup.completion.type.group")
          vm.foundGroups.collectUntilCompleted("Error fetching GitLab groups for completion") { groups ->
            customResult.addAllElements(groups.map {
              createLookupElement(it, it.path, Groups, it.name, groupType, DEFAULT_PRIORITY)
            })
          }
        }
      }
    }
  }

  private suspend fun <T> StateFlow<ComputedResult<List<T>>?>.collectUntilCompleted(
    errorMessage: String,
    onSuccess: (List<T>) -> Unit,
  ) = takeWhile { computedResult ->
    computedResult?.onSuccess { items ->
      onSuccess(items)
      return@takeWhile false
    }?.onFailure {
      LOG.error(errorMessage, it)
      return@takeWhile false
    }
    true
  }.collect()

  private fun createLookupElement(
    lookupObject: Any,
    shortName: @NlsSafe String,
    icon: Icon?,
    fullName: @NlsSafe String,
    type: @Nls String?,
    priority: Double,
  ): LookupElement {
    val elementBuilder =
      LookupElementBuilder.create(lookupObject, "@$shortName")
        .withIcon(icon)
        .withTailText(fullName)
        .withTypeText(type)
        .withPresentableText("@$shortName ")
        .withInsertHandler { context, _ ->
          val offset = context.selectionEndOffset
          if (context.selectionEndOffset == context.document.charsSequence.length || context.document.charsSequence[offset] != ' ') {
            context.document.insertString(offset, " ")
            context.editor.caretModel.moveToOffset(offset + 1)
          }
        }
    val element = fullName.let { elementBuilder.withLookupString("@$it") }
    return PrioritizedLookupElement.withPriority(element, priority)
  }
}