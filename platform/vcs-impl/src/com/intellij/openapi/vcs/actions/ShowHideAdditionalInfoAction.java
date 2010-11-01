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

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ShowHideAdditionalInfoAction extends AnAction {
  private boolean showAdditionalInfo = false;
  private final List<AnnotationFieldGutter> myGutters;
  private final EditorGutterComponentEx myGutter;
  private final AnnotateActionGroup myViewActions;

  public ShowHideAdditionalInfoAction(List<AnnotationFieldGutter> gutters, EditorGutterComponentEx gutter, AnnotateActionGroup viewActions) {
    myGutters = gutters;
    myGutter = gutter;
    myViewActions = viewActions;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    showAdditionalInfo = !showAdditionalInfo;
    for (AnnotationFieldGutter gutter : myGutters) {
      gutter.setShowAdditionalInfo(showAdditionalInfo);
    }
    myViewActions.setAvailable(!showAdditionalInfo);
    myGutter.revalidateMarkup();
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setText(showAdditionalInfo ? "Hide Additional Info" : "Show Additional Info");
  }
}
