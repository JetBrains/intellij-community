/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

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

  protected TestDataHighlightingPass(@NotNull final Project project, @Nullable final Document document) {
    super(project, document);
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    removeHighlighters();

    if (myDocument == null) {
      return;
    }
    final MarkupModel model = DocumentMarkupModel.forDocument(myDocument, myProject, true);
    final String text = myDocument.getText();

    if (text != null) {
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
  }

  private void removeHighlighters() {
    if (myDocument == null) {
      return;
    }
    final MarkupModel model = DocumentMarkupModel.forDocument(myDocument, myProject, true);
    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      if (highlighter.getUserData(KEY) == VALUE) {
        highlighter.dispose();
      }
    }
  }

  private static class MyGutterIconRenderer extends GutterIconRenderer implements DumbAware {
    @NotNull
    @Override
    public Icon getIcon() {
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
