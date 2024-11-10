// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import ai.grazie.detector.heuristics.rule.RuleFilter
import ai.grazie.utils.toLinkedSet
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.msg.CONFIG_STATE_TOPIC
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClassLoaderUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.spellchecker.grazie.SpellcheckerLifecycle
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.languagetool.JLanguageTool
import org.languagetool.rules.spelling.SpellingCheckRule
import java.util.concurrent.Callable

internal class GrazieSpellcheckerLifecycle : SpellcheckerLifecycle {
  override suspend fun preload(project: Project) {
    serviceAsync<GrazieCheckers>()
  }
}

@ApiStatus.Internal
@Service(Service.Level.APP)
class GrazieCheckers(coroutineScope: CoroutineScope) : GrazieStateLifecycle {

  private val MAX_SUGGESTIONS_COUNT = 3

  private val filter by lazy { RuleFilter.withAllBuiltIn() }

  private fun filterCheckers(word: String): Set<SpellerTool> {
    val checkers = this.checkers
    if (checkers.isEmpty()) {
      return emptySet()
    }

    val preferred = filter.filter(listOf(word)).preferred
    return checkers.asSequence()
      .filter { checker -> preferred.any { checker.lang.equalsTo(it) } }
      .toSet()
  }

  data class SpellerTool(val tool: JLanguageTool, val lang: Lang, val speller: SpellingCheckRule, val suggestLimit: Int) {
    fun check(word: String): Boolean? = synchronized(speller) {
      if (word.isBlank()) return true

      ClassLoaderUtil.computeWithClassLoader<Boolean, Throwable>(GraziePlugin.classLoader) {
        if (speller.match(tool.getRawAnalyzedSentence(word)).isEmpty()) {
          if (!speller.isMisspelled(word)) true
          else {
            // if the speller does not return matches, but the word is still misspelled (not in the dictionary),
            // then this word was ignored by the rule (e.g. alien word), and we cannot be sure about its correctness
            // let's try adding a small change to a word to see if it's alien
            val mutated = word + word.last() + word.last()
            if (speller.match(tool.getRawAnalyzedSentence(mutated)).isEmpty()) null else true
          }
        }
        else false
      }
    }

    fun suggest(text: String): Set<String> = synchronized(speller) {
      ClassLoaderUtil.computeWithClassLoader<Set<String>, Throwable>(GraziePlugin.classLoader) {
        speller.match(tool.getRawAnalyzedSentence(text))
          .flatMap { match ->
            match.suggestedReplacements.map {
              text.replaceRange(match.fromPos, match.toPos, it)
            }
          }
          .take(suggestLimit).toSet()
      }
    }
  }

  @Volatile
  private var checkers: Collection<SpellerTool> = heavyInit()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val configurationScope = coroutineScope.childScope("ConfigurationChanged", Dispatchers.Default.limitedParallelism(1))

  init {
    val application = ApplicationManager.getApplication()
    val connection = application.messageBus.connect(coroutineScope)
    connection.subscribe(CONFIG_STATE_TOPIC, this)
  }

  // getService() enables cancellable code to be canceled even when init of service is a long operation
  private fun heavyInit(): Collection<SpellerTool> {
    val set = LinkedHashSet<SpellerTool>()
    for (lang in GrazieConfig.get().availableLanguages) {
      if (lang.isEnglish()) continue

      val tool = LangTool.getTool(lang)
      tool.allSpellingCheckRules.firstOrNull()
        ?.let { set.add(SpellerTool(tool, lang, it, MAX_SUGGESTIONS_COUNT)) }
    }

    return set
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    configurationScope.launch {
      checkers = blockingContext { heavyInit() }
    }
  }

  fun isCorrect(word: String): Boolean? {
    val myCheckers = filterCheckers(word)

    var isAlien = true
    for (speller in myCheckers) {
      when (speller.check(word)) {
        true -> return true
        false -> isAlien = false
        else -> {}
      }
    }

    return if (isAlien) null else false
  }

  fun getSuggestions(word: String): Collection<String> {
    val filtered = filterCheckers(word)
    if (filtered.isEmpty()) {
      return emptyList()
    }

    val indicator = EmptyProgressIndicator.notNullize(ProgressManager.getGlobalProgressIndicator())
    return ApplicationUtil.runWithCheckCanceled(Callable {
      filtered
        .asSequence()
        .map { speller ->
          indicator.checkCanceled()
          speller.suggest(word)
        }
        .flatten()
        .toLinkedSet()
    }, indicator)
  }

  @TestOnly
  fun awaitConfiguration() {
    runBlockingMaybeCancellable {
      val jobs: List<Job> = configurationScope.coroutineContext.job.children.toList()
      joinAll(*jobs.toTypedArray())
    }
  }
}
