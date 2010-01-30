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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.util.registry.Registry;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ShowHideColorsAction extends AnAction {
  private boolean showColors = Registry.is("vcs.show.colored.annotations");
  private final List<AnnotationFieldGutter> myGutters;
  private final EditorGutterComponentEx myGutter;

  public ShowHideColorsAction(List<AnnotationFieldGutter> gutters, EditorGutterComponentEx gutter) {
    myGutters = gutters;
    myGutter = gutter;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showColors = !showColors;
    for (AnnotationFieldGutter gutter : myGutters) {
      gutter.setShowBg(showColors);
    }
    myGutter.revalidateMarkup();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(showColors ? "Hide Colors" : "Show Colors");
  }
}
