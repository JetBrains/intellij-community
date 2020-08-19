// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

/**
 * @author sergey.evdokimov
 */
public class MvcConfigureNotification extends GroovyFrameworkConfigNotification {

  private final MvcFramework framework;

  public MvcConfigureNotification(MvcFramework framework) {
    this.framework = framework;
  }

  @Override
  public boolean hasFrameworkStructure(@NotNull Module module) {
    return framework.hasFrameworkStructure(module) &&
           VfsUtil.findRelativeFile(framework.findAppRoot(module), "application.properties") != null;
  }

  @Override
  public boolean hasFrameworkLibrary(@NotNull Module module) {
    return framework.hasFrameworkJar(module);
  }

  @Override
  public EditorNotificationPanel createConfigureNotificationPanel(@NotNull final Module module) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(GroovyBundle.message("mvc.framework.0.not.configured.for.module.1", framework.getFrameworkName(), module.getName()));
    for (var action : framework.createConfigureActions(module).entrySet()) {
      panel.createActionLabel(action.getKey(), action.getValue());
    }
    return panel;
  }
}
