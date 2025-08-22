// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.encoding.EncodingProjectManager
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import java.nio.charset.StandardCharsets


@Suppress("NonAsciiCharacters")
class PropertiesSupportTest : GrazieTestBase() {
  override val enableGrazieChecker: Boolean = true

  fun `test grammar check in file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    EncodingProjectManager.getInstance(project).setDefaultCharsetForPropertiesFiles(null, StandardCharsets.UTF_8)
    EncodingProjectManager.getInstance(project).setNative2AsciiForPropertiesFiles(null, false)

    myFixture.configureByText(
      "a.properties",
      """
        one.typo=It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
        one.spellcheck.typo=It is <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human
        few.typos=It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> working for <GRAMMAR_ERROR descr="MUCH_COUNTABLE">much</GRAMMAR_ERROR> warnings
        ignore.template=It is {0} friend
        not.<TYPO descr="Typo: In word 'ignre'">ignre</TYPO>.other.mistakes=It is friend. But I have a {0} here

        about.box.build.date=, built on {0}

        ru.text.err1=В моей коробке лежало <GRAMMAR_ERROR descr="Sklonenije_NUM_NN">пять карандаша</GRAMMAR_ERROR>.
        ru.text.err2=А <GRAMMAR_ERROR descr="grammar_vse_li_noun">все ли ошибка</GRAMMAR_ERROR> найдены?
        ru.text.err3=Это случилось <GRAMMAR_ERROR descr="INVALID_DATE">31 ноября</GRAMMAR_ERROR> 2014 г.
        ru.text.err4=За весь вечер она <GRAMMAR_ERROR descr="ne_proronila_ni">не проронила и слово</GRAMMAR_ERROR>.
        ru.text.err5=Собрание состоится в <GRAMMAR_ERROR descr="RU_COMPOUNDS">конференц зале</GRAMMAR_ERROR>.
        ru.text.err6=<GRAMMAR_ERROR descr="WORD_REPEAT_RULE">Он он</GRAMMAR_ERROR> здесь ошибка в тексте.
        ru.with.newline=Не удалось авторизоваться.\nПопробуйте ещё раз.

        de.text.err1=Er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um <GRAMMAR_ERROR descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</GRAMMAR_ERROR>.
        de.text.err2=das ist <GRAMMAR_ERROR descr="FUEHR_FUER">führ</GRAMMAR_ERROR> Dich!
        de.text.err3=das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.

        message.text.files.do.not.exist=<html><body>The following files don''t exist: <br>\
          {0}The corresponding modules won''t be converted. Do you want to continue?</body></html>

        method.duplicates.found.message={0, choice, 1#1 code fragment|2#{0,number} code fragments} found
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test properties typos spellcheck performance`() {
    Benchmark.newBenchmark("Highlight typos in i18n.properties file") {
      runHighlightTestForFile("ide/language/properties/i18n.properties")
    }.setup {
      psiManager.dropPsiCaches()
      project.service<GrazieSpellCheckerEngine>().dropSuggestionCache()
    }.start()
  }
}
