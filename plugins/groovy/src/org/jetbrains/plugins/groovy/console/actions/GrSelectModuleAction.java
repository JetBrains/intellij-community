/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.console.GroovyConsole;
import org.jetbrains.plugins.groovy.console.GroovyConsoleStateService;
import org.jetbrains.plugins.groovy.console.GroovyConsoleUtil;

public class GrSelectModuleAction extends AnAction {

  private final GroovyConsoleStateService myProjectConsole;
  private final VirtualFile myFile;

  public GrSelectModuleAction(GroovyConsoleStateService console, VirtualFile file) {
    super(null, "Which module to use classpath of?", AllIcons.Nodes.Module);
    myProjectConsole = console;
    myFile = file;
  }

  @Override
  public boolean displayTextInToolbar() {
    return super.displayTextInToolbar();
  }

  @Override
  public void update(AnActionEvent e) {
    if (myProjectConsole.isProjectConsole(myFile)) {
      final Module module = myProjectConsole.getSelectedModule(myFile);
      e.getPresentation().setText(getText(module));
    }
    else {
      e.getPresentation().setVisible(false);
    }
  }

  @NotNull
  public static String getText(@Nullable Module module) {
    return module == null || module.isDisposed() ? "Select module..." : GroovyConsoleUtil.getTitle(module);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    GroovyConsoleUtil.selectModuleAndRun(project, new Consumer<Module>() {
      @Override
      public void consume(Module module) {
        final Module existingModule = myProjectConsole.getSelectedModule(myFile);
        if (module.equals(existingModule)) return;
        final GroovyConsole existingConsole = myFile.getUserData(GroovyConsole.GROOVY_CONSOLE);
        if (existingConsole != null) existingConsole.stop();
        myProjectConsole.setFileModule(myFile, module);
        myFile.putUserData(GroovyConsole.GROOVY_CONSOLE, null);
        ProjectView.getInstance(project).refresh();
      }
    }, e.getDataContext());
  }
}
