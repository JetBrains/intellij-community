// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

import javax.swing.*;
import java.util.function.Function;

/**
 * @author Maxim.Medvedev
 */
final class ConfigureGroovyLibraryNotificationProvider implements EditorNotificationProvider {

  @Override
  public @NotNull Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    try {
      if (!file.getFileType().equals(GroovyFileType.GROOVY_FILE_TYPE)) {
        return CONST_NULL;
      }
      // do not show the panel for Gradle build scripts
      // expecting groovy library to always be available at the gradle distribution
      if (StringUtil.endsWith(file.getName(), ".gradle") ||
          CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
        return CONST_NULL;
      }

      final Module module = ModuleUtilCore.findModuleForFile(file, project);
      if (module == null ||
          isMavenModule(module)) {
        return CONST_NULL;
      }

      for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensions()) {
        if (configNotification.hasFrameworkStructure(module)) {
          return configNotification.hasFrameworkLibrary(module) ?
                 CONST_NULL :
                 fileEditor -> createConfigureNotificationPanel(module, fileEditor);
        }
      }

      return CONST_NULL;
    }
    catch (ProcessCanceledException | IndexNotReadyException ignored) {
      return CONST_NULL;
    }
  }

  @RequiresEdt
  private static @NotNull EditorNotificationPanel createConfigureNotificationPanel(@NotNull Module module,
                                                                                   @NotNull FileEditor fileEditor) {
    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor);
    panel.setText(GroovyBundle.message("groovy.library.is.not.configured.for.module", module.getName()));
    panel.createActionLabel(GroovyBundle.message("configure.groovy.library"), () -> {
      AddCustomLibraryDialog.createDialog(new GroovyLibraryDescription(), module, null).show();
    });
    return panel;
  }

  private static boolean isMavenModule(@NotNull Module module) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild("pom.xml") != null) return true;
    }

    return false;
  }

}
