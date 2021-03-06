// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
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
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dmitry Avdeev
 */
public class DocumentMarkupModelTest extends BasePlatformTestCase {
  public void testInfoTestAttributes() {
    LanguageExtensionPoint<Annotator> extension = new LanguageExtensionPoint<>("TEXT", new TestAnnotator());
    extension.setPluginDescriptor(new DefaultPluginDescriptor("DocumentMarkupModelTest"));
    ExtensionTestUtil.maskExtensions(LanguageAnnotators.EP_NAME, Collections.singletonList(extension), myFixture.getTestRootDisposable());
    myFixture.configureByText(PlainTextFileType.INSTANCE, "foo");
    EditorColorsScheme scheme = new EditorColorsSchemeImpl(new DefaultColorsScheme()){{initFonts();}};
    scheme.setAttributes(HighlighterColors.TEXT, new TextAttributes(Color.black, Color.white, null, null, Font.PLAIN));
    ((EditorEx)myFixture.getEditor()).setColorsScheme(scheme);
    myFixture.doHighlighting();
    MarkupModel model = DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), getProject(), false);
    RangeHighlighter[] highlighters = model.getAllHighlighters();
    assertThat(highlighters).hasSize(1);
    TextAttributes attributes = highlighters[0].getTextAttributes(scheme);
    assertThat(attributes).isNotNull();
    assertThat(attributes.getBackgroundColor()).isNull();
    assertThat(attributes.getForegroundColor()).isNull();
  }

  public void testPersistentHighlighterUpdateOnPartialDocumentUpdate() {
    Document document = new DocumentImpl("line0\nline1\nline2");
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    RangeHighlighterEx highlighter = model.addPersistentLineHighlighter(null, 2, 0);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(document.getLineStartOffset(1), document.getTextLength()));
    assertFalse(highlighter.isValid());
  }

  public void testUpdateOfPersistentAndNormalHighlightersIsIndependent() {
    Document document = new DocumentImpl("\n\n\n\n\n");
    MarkupModelEx model = (MarkupModelEx)DocumentMarkupModel.forDocument(document, getProject(), true);
    RangeHighlighter h = model.addLineHighlighter(2, 0, null);
    model.addPersistentLineHighlighter(2, 0, null);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.deleteString(1, 4));
    assertFalse(h.isValid());
  }

  public static class TestAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).create();
    }
  }
}
