// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxProjectSdkSetupValidator extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  public static final Key<EditorNotificationPanel> KEY = Key.create("SdkSetupNotificationJavaFX");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  private static String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
    if (DumbService.isDumb(project)) {
      return null;
    }

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || module.isDisposed() || ModuleRootManager.getInstance(module).getSdk() == null) {
      return null;
    }

    final PsiClass nodeClass =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_SCENE_NODE, GlobalSearchScope.allScope(project));
    if (nodeClass == null) {
      return "The JavaFX runtime is not configured. " +
             "Either use a JDK that has the JavaFX built in, or add a JavaFX library to the classpath";
    }
    return null;
  }

  public void doFix(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    final String moduleName = module != null && !module.isDisposed() ? module.getName() : null;
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(moduleName, ClasspathEditor.NAME);
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (JavaFxFileTypeFactory.isFxml(file)) {
      String errorMessage = getErrorMessage(project, file);
      return errorMessage != null ? createPanel(errorMessage, () -> doFix(project, file)) : null;
    }
    return null;
  }

  @NotNull
  private static EditorNotificationPanel createPanel(@NotNull String message, @NotNull Runnable fix) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(message);
    panel.createActionLabel(ProjectBundle.message("project.sdk.setup"), fix);
    return panel;
  }
}
