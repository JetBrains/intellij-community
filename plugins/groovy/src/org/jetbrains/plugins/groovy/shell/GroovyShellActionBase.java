// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.shell;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

import java.util.Collection;
import java.util.List;

public abstract class GroovyShellActionBase extends AnAction {

  private final GroovyShellConfig myConfig;

  private final Condition<Module> APPLICABLE_MODULE = new Condition<Module>() {
    @Override
    public boolean value(Module module) {
      return myConfig.isSuitableModule(module);
    }
  };

  // non-static to distinguish different module acceptability conditions
  private final Key<CachedValue<Boolean>> APPLICABLE_MODULE_CACHE = Key.create("APPLICABLE_MODULE_CACHE");

  private final Consumer<Module> RUNNER = new Consumer<Module>() {
    @Override
    public void consume(final Module module) {
      GroovyShellRunnerImpl.doRunShell(myConfig, module);
    }
  };

  public GroovyShellActionBase(GroovyShellConfig runner) {
    myConfig = runner;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    boolean enabled = project != null && hasGroovyCompatibleModule(project);

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  private boolean hasGroovyCompatibleModule(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, APPLICABLE_MODULE_CACHE, () -> {
      Collection<Module> possibleModules = myConfig.getPossiblySuitableModules(project);
      return CachedValueProvider.Result.create(ModuleChooserUtil.hasGroovyCompatibleModules(possibleModules, APPLICABLE_MODULE),
                                               ProjectRootModificationTracker.getInstance(project));
    }, false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    List<Module> suitableModules = ModuleChooserUtil.filterGroovyCompatibleModules(myConfig.getPossiblySuitableModules(project),
                                                                                   APPLICABLE_MODULE);
    ModuleChooserUtil.selectModule(project, suitableModules, myConfig::getVersion, RUNNER);
  }
}
