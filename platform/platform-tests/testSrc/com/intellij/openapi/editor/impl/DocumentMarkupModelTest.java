/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Dmitry Avdeev
 */
public class DocumentMarkupModelTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testInfoTestAttributes() throws Exception {
    LanguageExtensionPoint<Annotator> extension = new LanguageExtensionPoint<>();
    extension.language="TEXT";
    extension.implementationClass = TestAnnotator.class.getName();
    PlatformTestUtil.registerExtension(ExtensionPointName.create(LanguageAnnotators.EP_NAME), extension, getTestRootDisposable());
    myFixture.configureByText(PlainTextFileType.INSTANCE, "foo");
    EditorColorsScheme scheme = new EditorColorsSchemeImpl(new DefaultColorsScheme()){{initFonts();}};
    scheme.setAttributes(HighlighterColors.TEXT, new TextAttributes(Color.black, Color.white, null, null, Font.PLAIN));
    ((EditorEx)myFixture.getEditor()).setColorsScheme(scheme);
    myFixture.doHighlighting();
    MarkupModel model = DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), getProject(), false);
    RangeHighlighter[] highlighters = model.getAllHighlighters();
    assertEquals(1, highlighters.length);
    TextAttributes attributes = highlighters[0].getTextAttributes();
    assertNotNull(attributes);
    assertNull(attributes.getBackgroundColor());
    assertNull(attributes.getForegroundColor());
  }

  public static class TestAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      holder.createInfoAnnotation(element, null);
    }
  }
}
