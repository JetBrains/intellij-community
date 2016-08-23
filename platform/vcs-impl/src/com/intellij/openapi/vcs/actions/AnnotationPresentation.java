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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class AnnotationPresentation implements TextAnnotationPresentation {
  @NotNull private final FileAnnotation myFileAnnotation;
  @NotNull private final UpToDateLineNumberProvider myUpToDateLineNumberProvider;
  @Nullable private final AnnotationSourceSwitcher mySwitcher;
  private final ArrayList<AnAction> myActions = new ArrayList<>();

  AnnotationPresentation(@NotNull FileAnnotation fileAnnotation,
                         @NotNull UpToDateLineNumberProvider upToDateLineNumberProvider,
                         @Nullable final AnnotationSourceSwitcher switcher) {
    myUpToDateLineNumberProvider = upToDateLineNumberProvider;
    myFileAnnotation = fileAnnotation;
    mySwitcher = switcher;
  }

  public EditorFontType getFontType(final int line) {
    VcsRevisionNumber revision = myFileAnnotation.originalRevision(line);
    VcsRevisionNumber currentRevision = myFileAnnotation.getCurrentRevision();
    return currentRevision != null && currentRevision.equals(revision) ? EditorFontType.BOLD : EditorFontType.PLAIN;
  }

  public ColorKey getColor(final int line) {
    if (mySwitcher == null) return AnnotationSource.LOCAL.getColor();
    return mySwitcher.getAnnotationSource(line).getColor();
  }

  public List<AnAction> getActions(int line) {
    int correctedNumber = myUpToDateLineNumberProvider.getLineNumber(line);
    for (AnAction action : myActions) {
      UpToDateLineNumberListener upToDateListener = ObjectUtils.tryCast(action, UpToDateLineNumberListener.class);
      if (upToDateListener != null) upToDateListener.consume(correctedNumber);

      LineNumberListener listener = ObjectUtils.tryCast(action, LineNumberListener.class);
      if (listener != null) listener.consume(line);
    }

    return myActions;
  }

  @NotNull
  public List<AnAction> getActions() {
    return myActions;
  }

  public void addAction(AnAction action) {
    myActions.add(action);
  }

  public void addAction(AnAction action, int index) {
    myActions.add(index, action);
  }
}
