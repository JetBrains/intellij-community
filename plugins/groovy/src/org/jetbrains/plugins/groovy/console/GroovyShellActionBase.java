/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.console.GroovyShellHandler;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

public abstract class GroovyShellActionBase extends AnAction {

  private final GroovyShellHandler myHandler;

  private final Condition<Module> APPLICABLE_MODULE = new Condition<Module>() {
    @Override
    public boolean value(Module module) {
      return myHandler.isSuitableModule(module) && myHandler.getRunner(module) != null;
    }
  };

  private final Function<Module, String> TITLE_PROVIDER = new Function<Module, String>() {
    @Override
    public String fun(Module module) {
      return myHandler.getRunner(module).getTitle(module);
    }
  };

  private final Consumer<Module> RUNNER = new Consumer<Module>() {
    @Override
    public void consume(final Module module) {
      myHandler.doRunShell(module);
    }
  };

  protected GroovyShellActionBase(GroovyShellHandler handler) {
    myHandler = handler;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    boolean enabled = project != null && !ModuleChooserUtil.getGroovyCompatibleModules(project, APPLICABLE_MODULE).isEmpty();

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    ModuleChooserUtil.selectModule(project, APPLICABLE_MODULE, TITLE_PROVIDER, RUNNER);
  }
}
