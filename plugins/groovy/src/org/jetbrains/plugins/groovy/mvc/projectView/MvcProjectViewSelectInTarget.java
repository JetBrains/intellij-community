/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureSynchronizer;

/**
 * @author Dmitry Krasilschikov
 */
public class MvcProjectViewSelectInTarget implements SelectInTarget, DumbAware {

  public boolean canSelect(SelectInContext context) {
    final Project project = context.getProject();
    final VirtualFile file = context.getVirtualFile();
    final MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(ModuleUtil.findModuleForFile(file, project));
    if (framework == null) {
      return false;
    }

    return MvcProjectViewPane.canSelectFile(project, framework, file);
  }

  public void selectIn(SelectInContext context, final boolean requestFocus) {
    final Project project = context.getProject();
    final VirtualFile file = context.getVirtualFile();

    final MvcFramework framework = MvcModuleStructureSynchronizer.getFramework(ModuleUtil.findModuleForFile(file, project));
    if (framework == null) {
      return;
    }

    final Runnable select = new Runnable() {
      public void run() {
        final MvcProjectViewPane view = MvcProjectViewPane.getView(project, framework);
        if (view != null) {
          view.selectFile(file, requestFocus);
        }
      }
    };

    if (requestFocus) {
      ToolWindowManager.getInstance(project).getToolWindow(MvcToolWindowDescriptor.getToolWindowId(framework)).activate(select, false);
    } else {
      select.run();
    }
  }

  public String getToolWindowId() {
    throw new UnsupportedOperationException();
  }

  public String toString() {
    return "Grails/Griffon View";
  }

  public String getMinorViewId() {
    throw new UnsupportedOperationException();
  }

  public float getWeight() {
    return (float)5.239;
  }

}
