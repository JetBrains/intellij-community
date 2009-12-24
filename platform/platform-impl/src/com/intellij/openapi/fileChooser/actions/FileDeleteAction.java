/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;

public class FileDeleteAction extends DeleteAction {
  public FileDeleteAction() {
    setEnabledInModalContext(true);
  }

  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return new VirtualFileDeleteProvider();
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final Boolean available = event.getData(FileChooserKeys.DELETE_ACTION_AVAILABLE);
    if (available != null && !available) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    super.update(event);
  }
}
