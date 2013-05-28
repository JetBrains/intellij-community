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
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition
import com.intellij.psi.codeStyle.arrangement.std.ArrangementSettingsToken
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens
import com.intellij.psi.codeStyle.arrangement.std.StdRulePriorityAwareSettings
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.junit.Assert

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.KEEP

/**
 * @author Denis Zhdanov
 * @since 7/20/12 2:54 PM
 */
abstract class AbstractRearrangerTest extends LightPlatformCodeInsightFixtureTestCase {
  
  static final def RICH_TEXT_HANDLERS = [ new RangeHandler(), new FoldingHandler() ]
  
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
  protected static ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type) {
    group(type, KEEP)
  }
  
  @NotNull
  protected static ArrangementGroupingRule group(@NotNull ArrangementSettingsToken type, @NotNull ArrangementSettingsToken order) {
    new ArrangementGroupingRule(type, order)
  }

  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull ArrangementSettingsToken token) {
    new StdArrangementMatchRule(new StdArrangementEntryMatcher(atom(token)))
  }

  @NotNull
  protected static StdArrangementMatchRule nameRule(@NotNull String nameFilter, @NotNull ArrangementSettingsToken ... tokens) {
    if (tokens.length == 0) {
      new StdArrangementMatchRule(new StdArrangementEntryMatcher(atom(nameFilter)))
    }
    else {
      def compositeCondition = ArrangementUtil.combine([atom(nameFilter)] + tokens.collect { atom(it) } as ArrangementAtomMatchCondition[])
      new StdArrangementMatchRule(new StdArrangementEntryMatcher(compositeCondition))
    }
  }
  
  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull ArrangementSettingsToken ... conditions) {
    rule(conditions.collect { atom(it) })
  }

  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull ArrangementAtomMatchCondition ... conditions) {
    def compositeCondition = ArrangementUtil.combine(conditions)
    new StdArrangementMatchRule(new StdArrangementEntryMatcher(compositeCondition))
  }

  @NotNull
  protected static StdArrangementMatchRule rule(@NotNull List<ArrangementAtomMatchCondition> conditions) {
    rule(conditions as ArrangementAtomMatchCondition[])
  }

  @NotNull
  protected static StdArrangementMatchRule ruleWithOrder(@NotNull ArrangementSettingsToken orderType,
                                                         @NotNull StdArrangementMatchRule rule)
  {
    new StdArrangementMatchRule(rule.matcher, orderType)
  }
  
  @NotNull
  protected static ArrangementAtomMatchCondition atom(@NotNull ArrangementSettingsToken token) {
    new ArrangementAtomMatchCondition(token)
  }

  @NotNull
  protected static ArrangementAtomMatchCondition atom(@NotNull String nameFilter) {
    new ArrangementAtomMatchCondition(StdArrangementTokens.Regexp.NAME, nameFilter)
  }
  
  protected void doTest(@NotNull args) {
    Info info = parse(args.initial)
    if (info.ranges && args.ranges) {
      junit.framework.Assert.fail(
      "Duplicate ranges info detected: explicitly given: $args.ranges, derived from markup: ${info.ranges}. Text:\n$args.initial"
      )
    }
    if (!info.ranges) {
      info.ranges = args.ranges ?: [TextRange.from(0, args.initial.length())]
    }
    
    myFixture.configureByText(fileType, info.text)

    def foldingModel = myFixture.editor.foldingModel
    
    info.foldings.each { FoldingInfo foldingInfo ->
      foldingModel.runBatchFoldingOperation {
        def region = foldingModel.addFoldRegion(foldingInfo.start, foldingInfo.end, foldingInfo.placeholder)
        region.expanded = false
      }
    }
    
    def settings = CodeStyleSettingsManager.getInstance(myFixture.project).currentSettings.getCommonSettings(language)
    settings.arrangementSettings = new StdRulePriorityAwareSettings(args.groups ?: [], args.rules ?: [])
    ArrangementEngine engine = ServiceManager.getService(myFixture.project, ArrangementEngine)
    engine.arrange(myFixture.editor, myFixture.file, info.ranges);
    
    // Check expectation.
    info = parse(args.expected)
    Assert.assertEquals(info.text, myFixture.editor.document.text)
    info.foldings.each {
      def foldRegion = foldingModel.getCollapsedRegionAtOffset(it.start)
      assertNotNull("Expected to find fold region at offset ${it.start}", foldRegion)
      assertEquals(it.end, foldRegion.endOffset)
    }
  }
  
  @NotNull
  private static def parse(@NotNull String text) {
    def handlers = [:]
    RICH_TEXT_HANDLERS.each { handlers["<${it.marker}"] = it }
    def result = new Info()
    def buffer = new StringBuilder(text)
    int offset = 0
    int richTextMarkStart = -1
    RichTextHandler handler = null
    while (offset < buffer.length()) {
      handlers.each { String key, RichTextHandler value ->
        int i = buffer.indexOf(key, offset)
        if (i >= 0 && (handler == null || i < richTextMarkStart)) {
          richTextMarkStart = i
          handler = value
        }
      }
      
      if (handler) {
        int openingTagEndOffset = buffer.indexOf('>', richTextMarkStart)
        int openTagLength = openingTagEndOffset - richTextMarkStart + 1
        def attributes = parseAttributes(buffer.substring(1 + richTextMarkStart + handler.marker.length(), openingTagEndOffset))
        
        def closingTag = "</${handler.marker}>"
        int closingTagStart = buffer.indexOf(closingTag)
        assert closingTagStart > 0
        int closingTagLength = 3 + handler.marker.length() // </marker>
        handler.handle(result, attributes, richTextMarkStart, closingTagStart - openTagLength)
        buffer.delete(closingTagStart, closingTagStart + closingTagLength)
        buffer.delete(richTextMarkStart, openingTagEndOffset + 1)
        offset = closingTagStart - openTagLength
        richTextMarkStart = -1
        handler = null
      }
      else {
        break
      }
    }
    result.text = buffer.toString()
    result
  }
  
  @NotNull
  private static Map<String, String> parseAttributes(@NotNull String text) {
    def result = [:]
    (text =~ /([^\s]+)=([^\s]+)/).each {
      result[it[1]] = it[2]
    }
    result
  }
}
