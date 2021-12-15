// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GrCastExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GrParenthesizedExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.completion.GroovyAutoPopupTest

class GroovyPostfixTemplatesTest extends GroovyAutoPopupTest {

  @Override
  void setUp() throws Exception {
    super.setUp();
    LiveTemplateCompletionContributor.setShowTemplatesInTests(false, myFixture.getTestRootDisposable())
  }

  @Override
  void tearDown() throws Exception {
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
                               @Nullable Class<? extends PostfixTemplate> expectedClass) {
    myFixture.configureByText "_.groovy", baseText
    type textToType
    Lookup lookup = myFixture.getLookup()
    if (expectedClass != null) {
      assertNotNull(lookup)
      LookupElement item = lookup.getCurrentItem()
      assertNotNull item
      assertInstanceOf item, PostfixTemplateLookupElement.class
      assertInstanceOf((item as PostfixTemplateLookupElement).getPostfixTemplate(), expectedClass)
      type ' '
      myFixture.checkResult result
    }
    else {
      2assertNull(lookup);
    }
  }

  void testPar() {
    doAutoPopupTest "1.<caret>", "par", "(1)", GrParenthesizedExpressionPostfixTemplate
  }

  void testCast() {
    doAutoPopupTest "1.<caret>", "cast", "1 as <caret>", GrCastExpressionPostfixTemplate
  }
}
