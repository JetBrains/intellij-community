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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.ide.util.frameworkSupport.AddFrameworkSupportDialog;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

/**
* @author sergey.evdokimov
*/
public class DefaultGroovyFrameworkConfigNotification extends GroovyFrameworkConfigNotification {

  @Override
  public boolean hasFrameworkStructure(@NotNull Module module) {
    return true;
  }

  @Override
  public boolean hasFrameworkLibrary(@NotNull Module module) {
    final Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module);
    return libraries.length > 0;
  }

  @Override
  public EditorNotificationPanel createConfigureNotificationPanel(final @NotNull Module module) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(GroovyBundle.message("groovy.library.is.not.configured.for.module", module.getName()));
    panel.createActionLabel(GroovyBundle.message("configure.groovy.library"), new Runnable() {
      @Override
      public void run() {
        AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
        if (dialog != null) {
          dialog.show();
        }
      }
    });
    return panel;
  }
}
