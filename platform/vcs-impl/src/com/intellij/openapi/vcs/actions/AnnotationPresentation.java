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
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.AnnotationSourceSwitcher;
import com.intellij.openapi.vcs.annotate.HighlightAnnotationsActions;
import com.intellij.openapi.vcs.annotate.TextAnnotationPresentation;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
class AnnotationPresentation implements TextAnnotationPresentation {
  private final HighlightAnnotationsActions myHighlighting;
  @Nullable
  private final AnnotationSourceSwitcher mySwitcher;
  private final List<AnAction> myActions;
  private SwitchAnnotationSourceAction mySwitchAction;
  private final List<Consumer<Integer>> myPopupLineNumberListeners;

  AnnotationPresentation(@NotNull final HighlightAnnotationsActions highlighting, @Nullable final AnnotationSourceSwitcher switcher,
                                final EditorGutterComponentEx gutter,
                                final List<AnnotationFieldGutter> gutters,
                                final AnAction... actions) {
    myHighlighting = highlighting;
    mySwitcher = switcher;
    myPopupLineNumberListeners = new LinkedList<Consumer<Integer>>();

    myActions = new ArrayList<AnAction>();
    myActions.add(Separator.getInstance());
    if (actions != null) {
      final List<AnAction> actionsList = Arrays.asList(actions);
      if (!actionsList.isEmpty()) {
        myActions.addAll(actionsList);
        myActions.add(new Separator());
      }
    }
    myActions.addAll(myHighlighting.getList());
    if (mySwitcher != null) {
      mySwitchAction = new SwitchAnnotationSourceAction(mySwitcher, gutter);
      myActions.add(mySwitchAction);
    }
    myActions.add(new ShowHideAdditionalInfoAction(gutters, gutter));
    myActions.add(new ShowHideColorsAction(gutters, gutter));
  }

  public void addLineNumberListener(final Consumer<Integer> listener) {
    myPopupLineNumberListeners.add(listener);
  }

  public EditorFontType getFontType(final int line) {
    return myHighlighting.isLineBold(line) ? EditorFontType.BOLD : EditorFontType.PLAIN;
  }

  public ColorKey getColor(final int line) {
    if (mySwitcher == null) return AnnotationSource.LOCAL.getColor();
    return mySwitcher.getAnnotationSource(line).getColor();
  }

  public List<AnAction> getActions(int line) {
    for (Consumer<Integer> listener : myPopupLineNumberListeners) {
      listener.consume(line);
    }
    return myActions;
  }

  public void addSourceSwitchListener(final Consumer<AnnotationSource> listener) {
    mySwitchAction.addSourceSwitchListener(listener);
  }
}
