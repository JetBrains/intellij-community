// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ui

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.collaboration.ui.codereview.avatar.Avatar
import com.intellij.collaboration.util.IncrementallyComputedValue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

private val LOG: Logger = logger<GithubMarkdownCompletionContributor>()

internal class GithubMarkdownCompletionContributor : CompletionContributor() {

  override fun fillCompletionVariants(
    parameters: CompletionParameters,
    result: CompletionResultSet,
  ) {
    val vmWithTextCompletion = parameters.editor.getUserData(GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY) ?: return

    vmWithTextCompletion.withMentionCompletionModel { vm ->
      val text = parameters.position.text.substringBefore(DUMMY_IDENTIFIER_TRIMMED).takeLastWhile { !it.isWhitespace() }

      if (!text.startsWith("@")) return@withMentionCompletionModel

      val customResult = result.withPrefixMatcher(text).caseInsensitive()

      runBlockingCancellable {
        processUsers(vm.pullRequestParticipants, vm.avatarIconsProvider, customResult, 10.0)
        processUsers(vm.mentionableUsers, vm.avatarIconsProvider, customResult, 1.0)
      }
    }
  }

  private suspend fun processUsers(
    users: StateFlow<IncrementallyComputedValue<List<GHUser>>>,
    avatarIconsProvider: GHAvatarIconsProvider,
    customResult: CompletionResultSet,
    priority: Double,
  ) {
    var counter = 0
    users.takeWhile {
      val batches: List<GHUser>? = it.valueOrNull
      if (batches != null && batches.size > counter) {
        val userSequence: Sequence<GHUser> = batches.subList(counter, batches.size).asSequence()
        val newElements: List<LookupElement> = userSequence.map { it: GHUser ->
          createLookupElement(avatarIconsProvider, it, priority)
        }.toList()
        customResult.addAllElements(newElements)
        counter = batches.size
      }

      if (it.isComplete) {
        return@takeWhile false
      }

      if (it.exceptionOrNull != null) {
        LOG.error("Error fetching GitHub users for completion", it.exceptionOrNull)
        return@takeWhile false
      }

      true
    }.collect()
  }

  private fun createLookupElement(
    avatarIconsProvider: GHAvatarIconsProvider,
    user: GHUser,
    priority: Double,
  ): LookupElement {
    val icon = avatarIconsProvider.getIcon(user.avatarUrl, Avatar.Sizes.SMALL)
    val elementBuilder =
      LookupElementBuilder.create(user, "@${user.login}")
        .withIcon(icon)
        .withTailText(user.name)
        .withPresentableText("@${user.login} ")
        .withInsertHandler { context, _ ->
          val offset = context.selectionEndOffset
          if (context.selectionEndOffset == context.document.charsSequence.length || context.document.charsSequence[offset] != ' ') {
            context.document.insertString(offset, " ")
            context.editor.caretModel.moveToOffset(offset + 1)
          }
        }
    val element = user.name?.let { elementBuilder.withLookupString("@$it") } ?: elementBuilder
    return PrioritizedLookupElement.withPriority(element, priority)
  }
}