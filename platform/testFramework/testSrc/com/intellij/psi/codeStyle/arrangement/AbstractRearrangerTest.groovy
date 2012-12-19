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
import org.jetbrains.annotations.Nullable
import org.junit.Assert

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
    settings.arrangementSettings = new StdArrangementSettings(args.groups ?: [], args.rules ?: [])
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
  
  private static class Info {
    String text
    @Nullable List<TextRange> ranges = []
    List<FoldingInfo> foldings = []
  }
  
  private static class FoldingInfo {
    def placeholder
    def start
    def end
  }
  
  private interface RichTextHandler {
    String getMarker()
    void handle(@NotNull Info info, @NotNull Map<String, String> attributes, int start, int end)
  }
  
  private static class RangeHandler implements RichTextHandler {
    @Override String getMarker() { "range" }

    @Override
    void handle(Info info, Map<String, String> attributes, int start, int end) {
      info.ranges << TextRange.create(start, end)
    }
  }
  
  private static class FoldingHandler implements RichTextHandler {
    @Override String getMarker() { "fold" }

    @Override
    void handle(Info info, Map<String, String> attributes, int start, int end) {
      info.foldings << new FoldingInfo(placeholder: attributes.text ?: '...', start: start, end: end)
    }
  }
}
