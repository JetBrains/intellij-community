package com.intellij.grazie.cloud

import ai.grazie.gec.model.CorrectionServiceType
import ai.grazie.gec.model.doc.Paragraph
import ai.grazie.gec.model.problem.Problem
import ai.grazie.gec.model.problem.SentenceWithProblems
import ai.grazie.gec.model.request.ClientAbility
import ai.grazie.gec.model.settings.StyleProfile
import ai.grazie.gec.model.settings.UserSettings
import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.exceptions.HTTPStatusException
import ai.grazie.model.cloud.exceptions.HttpExceptionBase
import ai.grazie.ner.model.SentenceWithNERAnnotations
import ai.grazie.nlp.langs.Language
import ai.grazie.rules.settings.TextStyle
import ai.grazie.rules.tree.TreeSupport
import ai.grazie.text.exclusions.SentenceWithExclusions
import ai.grazie.tree.model.SentenceWithTreeDependencies
import ai.grazie.utils.capitalize
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TreeRuleChecker
import com.intellij.grazie.utils.HighlightingUtil.findInstalledLang
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

object APIQueries {
  suspend fun correctText(paragraphs: List<Paragraph>, project: Project, services: Set<CorrectionServiceType>): List<Problem>? {
    val langs = paragraphs.asSequence()
      .mapNotNull { it.forcedLanguage }
      .mapNotNull { findInstalledLang(it) }
      .toSet()
    if (langs.isEmpty()) return emptyList()

    return handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.gec()?.correctText(
          paragraphs, services,
          getUserSettingsWithLanguageVariant(langs),
          setOf(ClientAbility.closeMlecMerging)
        )?.corrections
      }
    }
  }

  suspend fun mlec(sentences: List<SentenceWithExclusions>, lang: Language, project: Project): List<SentenceWithProblems>? {
    return handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.gec()?.problemsWithExclusions(
          lang, sentences, setOf(CorrectionServiceType.MLEC), clientAbilities = setOf(ClientAbility.closeMlecMerging)
        )
      }
    }
  }

  suspend fun trees(language: Language, modelName: String, parserOptions: List<String>, sentences: List<String>, project: Project): List<SentenceWithTreeDependencies>? =
    handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.meta()?.tree(modelName, parserOptions)?.parse(language, sentences)
      }
    }

  suspend fun nerAnnotations(language: Language, sentences: List<String>, project: Project): List<SentenceWithNERAnnotations>? =
    handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.meta()?.ner(TreeSupport.CLOUD_NER_VERSION)?.annotate(language, sentences)
      }
    }

  suspend fun spell(sentences: List<SentenceWithExclusions>, language: Language, project: Project): List<SentenceWithProblems>? {
    if (language !in ruleEngineLanguages) return null
    val lang = findInstalledLang(language)
    if (lang == null) return null
    return handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.gec()?.problemsWithExclusions(
          language, sentences, setOf(CorrectionServiceType.SPELL), getUserSettingsWithLanguageVariant(listOf(lang))
        )
      }
    }
  }

  private fun getUserSettingsWithLanguageVariant(langs: Collection<Lang>): UserSettings? {
    val paramValues = langs.mapNotNull { lang ->
      val variant = TreeRuleChecker.getLanguageVariant(lang)
      if (variant == null) return@mapNotNull null
      val prefix = lang.iso.toString().capitalize()
      StyleProfile.ParamValue("$prefix.variant", variant)
    }
    if (paramValues.isEmpty()) return null
    return UserSettings(
      customProfiles = arrayOf(
        StyleProfile(
          id = TextStyle.Unspecified.id,
          paramValues = paramValues.toTypedArray()
        )
      )
    )
  }

  @ApiStatus.Internal
  suspend fun <T> handleExceptions(project: Project, service: BackgroundCloudService? = null, compute: suspend () -> T?): T? =
    try {
      val result = compute()
      GrazieCloudNotifications.Connection.connectionStable(project, service)
      result
    }
    catch (e: HTTPStatusException.AccessProhibited) {
      thisLogger().info("Authorisation error in Grazie functionality", e)
      null
    }
    catch (e: HttpExceptionBase) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: HTTPConnectionError) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: HttpRequestTimeoutException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: IOException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      thisLogger().error(RuntimeException(e))
      null
    }
}