/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class DocumentMarkupModelTest extends LightPlatformCodeInsightFixtureTestCase {
  public void testInfoTestAttributes() {
    LanguageExtensionPoint<Annotator> extension = new LanguageExtensionPoint<>();
    extension.language="TEXT";
    extension.implementationClass = TestAnnotator.class.getName();
    PlatformTestUtil.registerExtension(ExtensionPointName.create(LanguageAnnotators.EP_NAME), extension, myFixture.getTestRootDisposable());
    myFixture.configureByText(PlainTextFileType.INSTANCE, "foo");
    EditorColorsScheme scheme = new EditorColorsSchemeImpl(new DefaultColorsScheme()){{initFonts();}};
    scheme.setAttributes(HighlighterColors.TEXT, new TextAttributes(Color.black, Color.white, null, null, Font.PLAIN));
    ((EditorEx)myFixture.getEditor()).setColorsScheme(scheme);
    myFixture.doHighlighting();
    MarkupModel model = DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), getProject(), false);
    RangeHighlighter[] highlighters = model.getAllHighlighters();
    assertThat(highlighters).hasSize(1);
    TextAttributes attributes = highlighters[0].getTextAttributes();
    assertThat(attributes).isNotNull();
    assertThat(attributes.getBackgroundColor()).isNull();
    assertThat(attributes.getForegroundColor()).isNull();
  }

  public void testPersistentHighlighterUpdateOnPartialDocumentUpdate() {
    Document document = new DocumentImpl("line0\nline1\nline2");
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    RangeHighlighterEx highlighter = model.addPersistentLineHighlighter(2, 0, null);
    new WriteCommandAction<Void>(getProject()){
      @Override
      protected void run(@NotNull Result<Void> result) {
        document.deleteString(document.getLineStartOffset(1), document.getTextLength());
      }
    }.execute();
    assertFalse(highlighter.isValid());
  }

  public static class TestAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      holder.createInfoAnnotation(element, null);
    }
  }
}
