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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

import java.util.Map.Entry;

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
    panel.setText(framework.getFrameworkName() + " SDK is not configured for module '" + module.getName() + '\'');
    for (Entry<String, Runnable> action : framework.createConfigureActions(module).entrySet()) {
      panel.createActionLabel(action.getKey(), action.getValue());
    }
    return panel;
  }
}
