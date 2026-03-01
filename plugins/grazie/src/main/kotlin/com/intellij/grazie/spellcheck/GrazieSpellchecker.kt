// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.spellcheck

import ai.grazie.detector.heuristics.rule.RuleFilter
import ai.grazie.utils.toLinkedSet
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieDynamic.dynamicFolder
import com.intellij.grazie.GraziePlugin
import com.intellij.grazie.ide.msg.CONFIG_STATE_TOPIC
import com.intellij.grazie.ide.msg.GrazieStateLifecycle
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.jlanguage.LangTool
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.ClassLoaderUtil.computeWithClassLoader
import com.intellij.platform.util.coroutines.childScope
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Absent
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Alien
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.languagetool.JLanguageTool
import org.languagetool.rules.spelling.SpellingCheckRule
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
@Service(Service.Level.APP)
class GrazieCheckers(coroutineScope: CoroutineScope) : GrazieStateLifecycle {

  private val filter by lazy { RuleFilter.withAllBuiltIn() }

  private fun isHunspellAvailable(lang: Lang, enabledLanguages: Set<Lang>): Boolean {
    val hunspell = lang.hunspellRemote ?: return false
    return lang in enabledLanguages && Files.exists(dynamicFolder.resolve(hunspell.file))
  }

  private fun filterCheckers(word: String): Set<SpellerTool> {
    val checkers = heavyInit()
    if (checkers.isEmpty()) {
      return emptySet()
    }

    val preferred = filter.filter(listOf(word)).preferred
    val enabledLanguages = GrazieConfig.get().enabledLanguages
    return checkers.asSequence()
      .filter { checker -> preferred.any { checker.lang.equalsTo(it) } }
      // Hunspell dictionary (if it's present) should do spellchecking / suggestions
      .filterNot { isHunspellAvailable(it.lang, enabledLanguages) }
      .toSet()
  }

  data class SpellerTool(val tool: JLanguageTool, val lang: Lang, val speller: SpellingCheckRule) {
    private val isFirstInvocation = AtomicBoolean(true)

    fun check(word: String): Boolean? {
      if (word.isBlank()) return true
      return synchronized(speller) {
        computeWithClassLoader<Boolean, Throwable>(GraziePlugin.classLoader) {
          runCancellableOnFirstInvocation {
            val sentence = tool.getRawAnalyzedSentence(word)
            // First token is always sentence start
            if (sentence.nonWhitespaceTokenCount <= 2) return@runCancellableOnFirstInvocation !speller.isMisspelled(word)
            if (speller.match(sentence).isEmpty()) {
              if (!speller.isMisspelled(word)) true
              else {
                // if the speller does not return matches, but the word is still misspelled (not in the dictionary),
                // then this word was ignored by the rule (e.g., alien word), and we cannot be sure about its correctness
                // let's try adding a small change to a word to see if it's alien
                val mutated = word + word.last() + word.last()
                if (speller.match(tool.getRawAnalyzedSentence(mutated)).isEmpty()) null else true
              }
            }
            else false
          }
        }
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun suggest(text: String): Set<String> = synchronized(speller) {
      computeWithClassLoader<Set<String>, Throwable>(GraziePlugin.classLoader) {
        runBlockingCancellable {
          computeDetached {
            speller.match(tool.getRawAnalyzedSentence(text))
              .flatMap { match ->
                match.suggestedReplacements.map {
                  text.replaceRange(match.fromPos, match.toPos, it)
                }
              }
              .toSet()
          }
        }
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun <T> runCancellableOnFirstInvocation(block: () -> T): T {
      if (!isFirstInvocation.get()) return block()
      val result = runBlockingCancellable {
        computeDetached {
          block()
        }
      }
      isFirstInvocation.set(false)
      return result
    }
  }

  @Volatile
  private var checkers: Set<SpellerTool>? = null

  @OptIn(ExperimentalCoroutinesApi::class)
  private val configurationScope = coroutineScope.childScope("ConfigurationChanged", Dispatchers.Default.limitedParallelism(1))

  init {
    val application = ApplicationManager.getApplication()
    val connection = application.messageBus.connect(coroutineScope)
    connection.subscribe(CONFIG_STATE_TOPIC, this)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun heavyInit(): Set<SpellerTool> {
    val checkers = this.checkers
    if (!checkers.isNullOrEmpty()) return checkers

    val langs = GrazieConfig.get().availableLanguages.filterNot { it.isEnglish() }
    val set = LinkedHashSet<SpellerTool>()
    for (lang in langs) {
      val tool = runBlockingCancellable {
        computeDetached {
          LangTool.getTool(lang, TextStyleDomain.Other)
        }
      }
      tool.allSpellingCheckRules.firstOrNull()
        ?.let { set.add(SpellerTool(tool, lang, it)) }
    }
    this.checkers = set
    return set
  }

  override fun update(prevState: GrazieConfig.State, newState: GrazieConfig.State) {
    if (prevState.availableLanguages == newState.availableLanguages) return
    checkers = null
    configurationScope.launch {
      heavyInit()
    }
  }

  fun hasSpellerTool(langs: List<Lang>): Boolean = langs.any { lang -> checkers?.any { it.lang == lang } ?: false }

  fun lookup(word: String): Dictionary.LookupStatus {
    val myCheckers = filterCheckers(word)

    var isAlien = true
    for (speller in myCheckers) {
      when (speller.check(word)) {
        true -> return Present
        false -> isAlien = false
        else -> {}
      }
    }

    return if (isAlien) Alien else Absent
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
