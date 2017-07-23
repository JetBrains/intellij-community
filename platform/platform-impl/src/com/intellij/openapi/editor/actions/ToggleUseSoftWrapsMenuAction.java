/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import org.jetbrains.annotations.NotNull;

/**
 * Action that toggles {@code 'show soft wraps at editor'} option and is expected to be used at various menus.
 *
 * @author Denis Zhdanov
 * @since Aug 19, 2010 3:15:26 PM
 */
public class ToggleUseSoftWrapsMenuAction extends AbstractToggleUseSoftWrapsAction {

  public ToggleUseSoftWrapsMenuAction() {
    super(SoftWrapAppliancePlaces.MAIN_EDITOR, false);
  }

  @Override
  public void update(@NotNull AnActionEvent e){
    super.update(e);
    if (!e.isFromActionToolbar()) {
      e.getPresentation().setIcon(null);
    }
    if (ActionPlaces.UNKNOWN.equals(e.getPlace())) {
      e.getPresentation().setText(ActionsBundle.message("action.EditorGutterToggleLocalSoftWraps.gutterText"));
    }
    e.getPresentation().setEnabled(getEditor(e) != null);
  }
}
