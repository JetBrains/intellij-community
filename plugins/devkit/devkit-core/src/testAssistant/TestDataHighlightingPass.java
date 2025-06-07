// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ui.PlatformColors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TestDataHighlightingPass extends TextEditorHighlightingPass {
  private static final Key<Object> KEY = Key.create("TestDataHighlighterKey");
  private static final Object VALUE = new Object();

  private static final GutterIconRenderer ICON_RENDERER = new MyGutterIconRenderer();

  private static final TextAttributes CARET_ATTRIBUTES = new TextAttributes(PlatformColors.BLUE, null, null, null, Font.BOLD);
  private static final String CARET = "<caret>";

  protected TestDataHighlightingPass(final @NotNull Project project, final @NotNull Document document) {
    super(project, document);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    removeHighlighters();

    final MarkupModel model = DocumentMarkupModel.forDocument(myDocument, myProject, true);
    final String text = myDocument.getText();

    int ind = -1;
    while ((ind = text.indexOf(CARET, ind + 1)) >= 0) {
      final RangeHighlighter highlighter = model.addRangeHighlighter(ind,
                                                                     ind + CARET.length(),
                                                                     HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                     CARET_ATTRIBUTES,
                                                                     HighlighterTargetArea.EXACT_RANGE);
        highlighter.setGutterIconRenderer(ICON_RENDERER);
        highlighter.putUserData(KEY, VALUE);
    }
  }

  private void removeHighlighters() {
    final MarkupModel model = DocumentMarkupModel.forDocument(myDocument, myProject, true);
    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      if (highlighter.getUserData(KEY) == VALUE) {
        highlighter.dispose();
      }
    }
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    @Override
    public @NotNull Icon getIcon() {
      return AllIcons.RunConfigurations.Junit;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MyGutterIconRenderer;
    }
    @Override
    public int hashCode() {
      return getIcon().hashCode();
    }
  }
}
