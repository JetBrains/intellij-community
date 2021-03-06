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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

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
    return JavaPsiFacade.getInstance(module.getProject()).findClass(
      GroovyCommonClassNames.GROOVY_OBJECT, module.getModuleWithDependenciesAndLibrariesScope(true)
    ) != null;
  }

  @Override
  public EditorNotificationPanel createConfigureNotificationPanel(@NotNull final Module module, @NotNull FileEditor fileEditor) {
    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(GroovyBundle.message("groovy.library.is.not.configured.for.module", module.getName()));
    panel.createActionLabel(GroovyBundle.message("configure.groovy.library"), () -> AddCustomLibraryDialog.createDialog(new GroovyLibraryDescription(), module, null).show());
    return panel;
  }
}
