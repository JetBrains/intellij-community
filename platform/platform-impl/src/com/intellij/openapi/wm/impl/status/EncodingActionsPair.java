/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ConvertFileEncodingAction;
import com.intellij.openapi.vfs.encoding.ReloadFileInOtherEncodingAction;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.InputEvent;

public class EncodingActionsPair {
  private final ConvertFileEncodingAction convert = new ConvertFileEncodingAction();
  private final ReloadFileInOtherEncodingAction reload = new ReloadFileInOtherEncodingAction();

  public boolean areActionsEnabled(InputEvent e,Editor editor, Component component, VirtualFile selectedFile, Project project) {
    DataContext dataContext = createDataContext(editor, component, selectedFile, project);
    convert.update(new AnActionEvent(e, dataContext, "", convert.getTemplatePresentation(), ActionManager.getInstance(), 0));
    reload.update(new AnActionEvent(e, dataContext, "", reload.getTemplatePresentation(), ActionManager.getInstance(), 0));
    return convert.getTemplatePresentation().isEnabled() || reload.getTemplatePresentation().isEnabled();
  }

  @NotNull
  public static DataContext createDataContext(Editor editor, Component component, VirtualFile selectedFile, Project project) {
    DataContext parent = DataManager.getInstance().getDataContext(component);
    return SimpleDataContext.getSimpleContext(PlatformDataKeys.VIRTUAL_FILE.getName(), selectedFile,
           SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), project,
           SimpleDataContext.getSimpleContext(PlatformDataKeys.CONTEXT_COMPONENT.getName(), editor == null ? null : editor.getComponent(),
                                              parent)));
  }

  public DefaultActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(convert);
    group.add(reload);

    return group;
  }

}
