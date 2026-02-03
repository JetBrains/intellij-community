package com.intellij.grazie.cloud

import ai.grazie.def.WordDefinition
import ai.grazie.gec.model.CorrectionServiceType
import ai.grazie.gec.model.problem.SentenceWithProblems
import ai.grazie.gen.tasks.text.rewrite.full.RewriteFullTaskDescriptor
import ai.grazie.gen.tasks.text.rewrite.full.RewriteFullTaskParams
import ai.grazie.gen.tasks.text.rewrite.selection.RewriteSelectionV2TaskDescriptor
import ai.grazie.gen.tasks.text.rewrite.selection.RewriteSelectionV2TaskParams
import ai.grazie.gen.tasks.text.translate.TextTranslateTaskDescriptor
import ai.grazie.gen.tasks.text.translate.TextTranslateTaskParams
import ai.grazie.model.cloud.exceptions.HTTPConnectionError
import ai.grazie.model.cloud.exceptions.HTTPStatusException
import ai.grazie.model.cloud.exceptions.HttpExceptionBase
import ai.grazie.model.cloud.sse.continuous.ContinuousSSEException
import ai.grazie.ner.model.SentenceWithNERAnnotations
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.langs.utils.englishName
import ai.grazie.rules.tree.TreeSupport
import ai.grazie.text.TextRange
import ai.grazie.text.exclusions.SentenceWithExclusions
import ai.grazie.tree.model.SentenceWithTreeDependencies
import ai.grazie.utils.text
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.utils.HighlightingUtil.findInstalledLang
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.io.computeDetached
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import com.intellij.openapi.util.TextRange as IJTextRange

object APIQueries {
  @JvmField
  val defLanguages = setOf(Language.ENGLISH, Language.GERMAN)

  @Volatile
  @JvmStatic
  var translator: Translator = object : Translator {
    override fun translate(texts: List<String>, toLang: Language, project: Project): List<String>? =
      request(project, null) {
        val taskClient = GrazieCloudConnector.api()?.tasksWithStreamData() ?: return@request null
        texts.map { text ->
          taskClient.executeV2(TextTranslateTaskDescriptor.createCallData(TextTranslateTaskParams(text, toLang.englishName)))
            .text { it.content }
        }
      }
  }

  @Volatile
  @JvmStatic
  var rephraser: Rephraser = object : Rephraser {
    override fun rephrase(text: String, ranges: List<IJTextRange>, language: Language, project: Project): List<Pair<IJTextRange, List<String>>>? =
      request(project, null) {
        coroutineScope {
          ranges.map {
            async { rephrase(text, it, language) }
          }.awaitAll().filterNotNull()
        }
      }

    private suspend fun rephrase(text: String, range: IJTextRange, language: Language): Pair<IJTextRange, List<String>>? {
      val taskClient = GrazieCloudConnector.api()?.tasksWithStreamData() ?: return null
      val taskCall = if (range.startOffset == 0 && range.length == text.length) {
        RewriteFullTaskDescriptor.createCallData(
          RewriteFullTaskParams(text, language.englishName)
        )
      }
      else {
        val contentPrefix = text.take(range.startOffset)
        val contentSuffix = text.drop(range.endOffset)
        RewriteSelectionV2TaskDescriptor.createCallData(
          RewriteSelectionV2TaskParams(
            contentPrefix, contentSuffix, language.englishName, range.substring(text))
        )
      }
      return range to taskClient.executeV2(taskCall)
        .text { it.content }
        .split("<rephrasing>")
        .filter { it.isNotBlank() }
        .map { it.trim() }
        .distinct()
        .filter { it != text }
    }
  }

  @JvmStatic
  fun definitions(
    text: String, range: IJTextRange, lang: Language, project: Project
  ): WordDefinition? {
    return request(project, null) {
      GrazieCloudConnector.api()?.meta()?.def()?.define(text, TextRange(range.startOffset, range.endOffset), lang)
    }
  }

  suspend fun mlec(sentences: List<SentenceWithExclusions>, lang: Language, project: Project): List<SentenceWithProblems>? {
    return handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.gec()?.problemsWithExclusions(lang, sentences, setOf(CorrectionServiceType.MLEC))
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
    if (language !in ruleEngineLanguages || findInstalledLang(language) == null) return null
    return handleExceptions(project, BackgroundCloudService.GEC) {
      withContext(Dispatchers.IO) {
        GrazieCloudConnector.api()?.gec()?.problemsWithExclusions(language, sentences, setOf(CorrectionServiceType.SPELL))
      }
    }
  }

  @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
  private fun <T> request(project: Project, service: BackgroundCloudService?, compute: suspend () -> T?): T? {
    return runBlockingCancellable {
      @Suppress("UnstableApiUsage")
      computeDetached { handleExceptions<T>(project, service, compute) }
    }
  }

  private suspend fun <T> handleExceptions(project: Project, service: BackgroundCloudService?, compute: suspend () -> T?): T? =
    try {
      val result = compute()
      GrazieCloudNotifications.Connection.connectionStable(project, service)
      result
    } catch (e: ContinuousSSEException.PrematureEnd) {
      thisLogger().warn(e)
      throw PrematureEndException()
    } catch (e: ContinuousSSEException.Error) {
      thisLogger().warn(e)
      throw ErrorException()
    } catch (e: ContinuousSSEException) {
      thisLogger().warn(e)
      throw TaskServerException()
    } catch (e: HTTPStatusException.AccessProhibited) {
      thisLogger().info("Authorisation error in Grazie functionality", e)
      null
    } catch (e: HttpExceptionBase) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    } catch (e: HTTPConnectionError) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    } catch (e: HttpRequestTimeoutException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    } catch (e: IOException) {
      GrazieCloudNotifications.Connection.connectionError(project, service, e)
      null
    } catch (e: CancellationException) {
      throw e
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Throwable) {
      thisLogger().error(RuntimeException(e))
      null
    }

  @TestOnly
  @JvmStatic
  fun overrideTranslator(translator: Translator, parent: Disposable) {
    val prev = this.translator
    this.translator = translator
    Disposer.register(parent) { this.translator = prev }
  }

  @TestOnly
  @JvmStatic
  fun overrideRephraser(rephraser: Rephraser, parent: Disposable) {
    val prev = this.rephraser
    this.rephraser = rephraser
    Disposer.register(parent) { this.rephraser = prev }
  }
}

interface Translator {
  fun translate(texts: List<String>, toLang: Language, project: Project): List<String>?
}

interface Rephraser {
  fun rephrase(text: String, ranges: List<IJTextRange>, language: Language, project: Project): List<Pair<IJTextRange, List<String>>>?
}

open class TaskServerException: RuntimeException()
class PrematureEndException : TaskServerException()
class ErrorException : TaskServerException()