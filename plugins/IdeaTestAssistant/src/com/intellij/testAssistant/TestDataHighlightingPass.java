/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testAssistant;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author Konstantin Bulenkov
 */
public class TestDataHighlightingPass extends TextEditorHighlightingPass {
  private static final Key<Object> KEY = Key.create("TestDataHighlighterKey");
  private static final Object VALUE = new Object();

  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/junit.png");
  private static final GutterIconRenderer ICON_RENDERER = new GutterIconRenderer() {
    @NotNull
    @Override
    public Icon getIcon() {
      return ICON;
    }
  };

  private static final TextAttributes CARET_ATTRIBUTES = new TextAttributes(Color.BLUE, null, null, null, Font.BOLD);
  private static final String CARET = "<caret>";

  protected TestDataHighlightingPass(@NotNull final Project project, @Nullable final Document document) {
    super(project, document);
  }

  @Override
  public void doCollectInformation(ProgressIndicator progress) {
  }

  @Override
  public void doApplyInformationToEditor() {
    removeHighlighters();

    final MarkupModel model = myDocument.getMarkupModel(myProject);
    final String text = myDocument.getText();

    if (text != null) {
      int ind = -1;
      while ((ind = text.indexOf(CARET, ind + 1)) > 0) {
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
    final MarkupModel model = myDocument.getMarkupModel(myProject);
    ArrayList<RangeHighlighter> toRemove = new ArrayList<RangeHighlighter>();
    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      if (highlighter.getUserData(KEY) == VALUE) {
        toRemove.add(highlighter);
      }
    }
    for (RangeHighlighter highlighter : toRemove) {
      model.removeHighlighter(highlighter);
    }
  }
}
