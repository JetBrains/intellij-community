// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GrCastExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GrForeachPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GrParenthesizedExpressionPostfixTemplate

class GroovyPostfixTemplatesTest extends JavaCompletionAutoPopupTestCase {

  @Override
  void setUp() throws Exception {
    super.setUp();
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = true
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable())
  }

  @Override
  void tearDown() throws Exception {
    CodeInsightSettings.instance.selectAutopopupSuggestionsByChars = false
    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance()
    assertNotNull(settings)
    settings.setProviderToDisabledTemplates(new HashMap<>())
    settings.setPostfixTemplatesEnabled(true)
    settings.setTemplatesCompletionEnabled(true)
    super.tearDown()
  }

  private void doAutoPopupTest(@NotNull String baseText,
                               @NotNull String textToType,
                               @NotNull String result,
                               @NotNull Class<? extends PostfixTemplate> expectedClass) {
    myFixture.configureByText "_.groovy", baseText
    type textToType
    Lookup lookup = myFixture.getLookup()
    assertNotNull(lookup)
    LookupElement item = lookup.getCurrentItem()
    assertNotNull item
    assertInstanceOf item, PostfixTemplateLookupElement.class
    assertInstanceOf((item as PostfixTemplateLookupElement).getPostfixTemplate(), expectedClass)
    type ' '
    myFixture.checkResult result
  }

  private void doNoPopupTest(@NotNull String baseText, @NotNull String textToType, @NotNull Class<? extends PostfixTemplate> clazz) {
    myFixture.configureByText "_.groovy", baseText
    type textToType
    Lookup lookup = myFixture.getLookup()
    assertNotNull(lookup)
    LookupElement item = lookup.getCurrentItem()
    assertNotNull item
    assertTrue !clazz.isInstance(item)
  }

  void testPar() {
    doAutoPopupTest "1.<caret>", "par", "(1)", GrParenthesizedExpressionPostfixTemplate
  }

  void testCast() {
    doAutoPopupTest "1.<caret>", "cast", "1 as <caret>", GrCastExpressionPostfixTemplate
  }

  void testFor() {
    doAutoPopupTest "[1, 2, 3].<caret>", "for", "for (final def  in [1, 2, 3]) {\n    \n}", GrForeachPostfixTemplate
  }

  void testNoForWithNonIterable() {
    doNoPopupTest "1.<caret>", "for", GrForeachPostfixTemplate
  }
}
