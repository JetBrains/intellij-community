/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ShowAnnotationColorsAction extends ToggleAction {
  public static final String KEY = "vcs.show.colored.annotations";
  private final List<AnnotationFieldGutter> myGutters;
  private final EditorGutterComponentEx myGutter;

  public ShowAnnotationColorsAction(List<AnnotationFieldGutter> gutters, EditorGutterComponentEx gutter) {
    super("Colors");
    myGutters = gutters;
    myGutter = gutter;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return isColorsEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance().setValue(KEY, String.valueOf(state));
    for (AnnotationFieldGutter gutter : myGutters) {
      gutter.setShowBg(state);
    }
    myGutter.revalidateMarkup();
  }

  public static boolean isColorsEnabled() {
    return PropertiesComponent.getInstance().getBoolean(KEY, true);
  }
}
