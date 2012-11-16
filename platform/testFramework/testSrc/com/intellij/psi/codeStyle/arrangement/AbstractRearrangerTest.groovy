/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement

import com.intellij.lang.Language
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:54 PM
 */
abstract class AbstractRearrangerTest extends LightPlatformCodeInsightFixtureTestCase {
  
  FileType fileType
  Language language;
  
  @Override
  protected void setUp() {
    super.setUp()
    CodeStyleSettingsManager.getInstance(myFixture.project).temporarySettings = new CodeStyleSettings()
  }

  @Override
  protected void tearDown() {
    CodeStyleSettingsManager.getInstance(myFixture.project).dropTemporarySettings()
    super.tearDown()
  }

  @NotNull
  protected CommonCodeStyleSettings getCommonSettings() {
    CodeStyleSettingsManager.getInstance(myFixture.project).currentSettings.getCommonSettings(language)
  }

  @NotNull
  protected static ArrangementGroupingRule group(@NotNull ArrangementGroupingType type) {
    group(type, ArrangementEntryOrderType.KEEP)
  }
  
  @NotNull
  protected static ArrangementGroupingRule group(@NotNull ArrangementGroupingType type, @NotNull ArrangementEntryOrderType order) {
    new ArrangementGroupingRule(type, order)
  }

  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull Object ... conditions) {
    rule(ArrangementEntryOrderType.KEEP, conditions)
  }
  
  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull ArrangementEntryOrderType orderType, @NotNull Object ... conditions) {
    def condition
    if (conditions.length == 1) {
      condition = atom(conditions[0])
    }
    else {
      condition = ArrangementUtil.combine(conditions.collect { atom(it) } as ArrangementMatchCondition[])
    }
    
    new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), orderType)
  }
  
  @NotNull
  protected static ArrangementAtomMatchCondition atom(@NotNull Object condition) {
    new ArrangementAtomMatchCondition(ArrangementUtil.parseType(condition), condition)
  }
  
  protected void doTest(@NotNull args) {
    def (String textToUse, List<TextRange> rangesToUse) = parseRanges(args.initial)
    if (rangesToUse && args.ranges) {
      junit.framework.Assert.fail(
      "Duplicate ranges info detected: explicitly given: $args.ranges, derived from markup: $rangesToUse. Text:\n$args.initial"
      )
    }
    if (!rangesToUse) {
      rangesToUse = args.ranges ?: [TextRange.from(0, args.initial.length())]
    }
    
    myFixture.configureByText(fileType, textToUse)
    def settings = CodeStyleSettingsManager.getInstance(myFixture.project).currentSettings.getCommonSettings(language)
    settings.arrangementSettings = new StdArrangementSettings(args.groups ?: [], args.rules ?: [])
    ArrangementEngine engine = ServiceManager.getService(myFixture.project, ArrangementEngine)
    engine.arrange(myFixture.file, rangesToUse);
    junit.framework.Assert.assertEquals(args.expected, myFixture.editor.document.text);
  }
  
  @NotNull
  private static def parseRanges(@NotNull String text) {
    def clearText = new StringBuilder(text)
    def ranges = []
    int shift = 0
    int shiftIncrease = '<range>'.length() * 2 + 1
    def match = text =~ '(?is)<range>.*?</range>'
    match.each {
      ranges << TextRange.create(match.start() - shift, match.end() - shift - shiftIncrease)
      clearText.delete(match.end() - '</range>'.length() - shift, match.end() - shift)
      clearText.delete(match.start() - shift, match.start() + '<range>'.length() - shift)
      shift += shiftIncrease
    }
    
    [clearText.toString(), ranges]
  }
}
