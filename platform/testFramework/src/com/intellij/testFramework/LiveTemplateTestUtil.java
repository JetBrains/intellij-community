// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LiveTemplateTestUtil {
  /**
   * Moves caret though opened file from start to the end and checks if template with {@code templateKey} from the {@code templateGroup} is
   * available in every position. Marks up opened file with {@code <available>} and {@code </available>} tags for the ranges where template
   * is available according to configured contexts and compares result with file from {@code answerFilePath}
   */
  public static void doTestTemplateExpandingAvailability(@NotNull CodeInsightTestFixture codeInsightTestFixture,
                                                         @NotNull String templateKey,
                                                         @NotNull String templateGroup,
                                                         @NotNull String answerFilePath) {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate(templateKey, templateGroup);
    TestCase.assertNotNull("Unable to find template: " + templateKey + "@" + templateGroup, template);

    Editor editor = codeInsightTestFixture.getEditor();
    Document document = editor.getDocument();
    CharSequence documentCharsSequence = document.getCharsSequence();
    CaretModel caretModel = editor.getCaretModel();
    PsiFile psiFile = codeInsightTestFixture.getFile();
    boolean isAvailable = false;
    List<Pair<Integer, String>> markers = new ArrayList<>();
    for (int offset = 0; offset < documentCharsSequence.length(); offset++) {
      caretModel.moveToOffset(offset);
      TemplateActionContext templateActionContext = TemplateActionContext.expanding(psiFile, editor);
      if (TemplateManagerImpl.isApplicable(template, templateActionContext)) {
        if (!isAvailable) {
          isAvailable = true;
          markers.add(Pair.create(offset, "<available>"));
        }
      }
      else if (isAvailable) {
        markers.add(Pair.create(offset - 1, "</available>"));
        isAvailable = false;
      }
    }
    if (isAvailable) {
      markers.add(Pair.create(documentCharsSequence.length(), "</available>"));
    }
    StringBuilder result = new StringBuilder(documentCharsSequence);
    ContainerUtil.reverse(markers).forEach(it -> result.insert(it.first, it.second));
    UsefulTestCase.assertSameLinesWithFile(answerFilePath, result.toString());
  }

  /**
   * Makes a selection in editor from every offset to the caret and checks if if template with {@code templateKey} from the
   * {@code templateGroup} is available. Marks up opened file with {@code <available>} and {@code </available>} tags for the ranges where
   * template is available according to configured contexts and compares result with file from {@code answerFilePath}
   */
  public static void doTestTemplateSurroundingAvailability(@NotNull CodeInsightTestFixture fixture,
                                                           @NotNull String templateKey,
                                                           @NotNull String templateGroup,
                                                           @NotNull String answerFilePath) {
    TemplateImpl template = TemplateSettings.getInstance().getTemplate(templateKey, templateGroup);
    String templateId = templateKey + "@" + templateGroup;
    TestCase.assertNotNull("Unable to find template: " + templateId, template);
    TestCase.assertTrue(templateId + " is not a selection template", template.isSelectionTemplate());

    Editor editor = fixture.getEditor();
    SelectionModel selectionModel = editor.getSelectionModel();
    Document document = editor.getDocument();
    CharSequence documentCharsSequence = document.getCharsSequence();
    CaretModel caretModel = editor.getCaretModel();
    int caretOffset = caretModel.getOffset();
    PsiFile psiFile = fixture.getFile();
    boolean isAvailable = false;
    List<Pair<Integer, String>> markers = new ArrayList<>();
    for (int offset = 0; offset < documentCharsSequence.length(); offset++) {
      selectionModel.setSelection(Math.min(offset, caretOffset), Math.max(offset, caretOffset));
      if (caretOffset == offset) {
        markers.add(Pair.create(offset, "<caret>"));
      }
      TemplateActionContext templateActionContext = TemplateActionContext.surrounding(psiFile, editor);
      if (TemplateManagerImpl.isApplicable(template, templateActionContext)) {
        if (!isAvailable) {
          isAvailable = true;
          markers.add(Pair.create(offset, "<available>"));
        }
      }
      else if (isAvailable) {
        markers.add(Pair.create(offset - 1, "</available>"));
        isAvailable = false;
      }
    }
    if (isAvailable) {
      markers.add(Pair.create(documentCharsSequence.length(), "</available>"));
    }
    StringBuilder result = new StringBuilder(documentCharsSequence);
    ContainerUtil.reverse(markers).forEach(it -> result.insert(it.first, it.second));
    UsefulTestCase.assertSameLinesWithFile(answerFilePath, result.toString());
  }
}
