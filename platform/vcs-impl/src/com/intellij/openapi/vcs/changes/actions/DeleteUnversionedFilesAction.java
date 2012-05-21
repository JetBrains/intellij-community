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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 02.11.2006
 * Time: 22:14:56
 */
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.util.IconUtil;

public class DeleteUnversionedFilesAction extends AnAction {
  public DeleteUnversionedFilesAction() {
    super("Delete", "Delete", IconUtil.getRemoveIcon());
  }

  public void actionPerformed(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
    if (deleteProvider == null) return;
    deleteProvider.deleteElement(e.getDataContext());
  }

  @Override
  public void update(AnActionEvent e) {
    DeleteProvider deleteProvider = e.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER);
    e.getPresentation().setVisible(deleteProvider != null && deleteProvider.canDeleteElement(e.getDataContext()));
  }
}