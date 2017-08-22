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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.vcsUtil.VcsUtil;

/**
 * @author Konstantin Bulenkov
 */
public class ShowHideAspectAction extends ToggleAction implements DumbAware {
  private final AnnotationFieldGutter myGutter;
  private boolean isAvailable = true;

  public ShowHideAspectAction(AnnotationFieldGutter gutter) {
    super(gutter.getID());
    myGutter = gutter;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myGutter.isAvailable();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    VcsUtil.setAspectAvailability(myGutter.getID(), state);

    AnnotateActionGroup.revalidateMarkupInAllEditors();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable);
  }

  public void setAvailable(boolean available) {
    isAvailable = available;
  }
}
