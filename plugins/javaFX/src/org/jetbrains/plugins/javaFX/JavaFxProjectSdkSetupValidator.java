// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import com.intellij.codeInsight.daemon.ProjectSdkSetupValidator;
import com.intellij.codeInsight.daemon.impl.JavaProjectSdkSetupValidator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.ActionHandler;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

import javax.swing.event.HyperlinkEvent;

public final class JavaFxProjectSdkSetupValidator implements ProjectSdkSetupValidator {

  @Override
  public boolean isApplicableFor(@NotNull Project project, @NotNull VirtualFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  @Override
  public @Nullable String getErrorMessage(@NotNull Project project, @NotNull VirtualFile file) {
    final String javaErrorMessage = JavaProjectSdkSetupValidator.INSTANCE.getErrorMessage(project, file);
    if (javaErrorMessage != null) {
      return javaErrorMessage;
    }
    if (DumbService.isDumb(project)) {
      return null;
    }
    final PsiClass nodeClass =
      JavaPsiFacade.getInstance(project).findClass(JavaFxCommonNames.JAVAFX_SCENE_NODE, GlobalSearchScope.allScope(project));
    if (nodeClass == null) {
      return JavaFXBundle.message("javafx.project.sdk.setup.validator.runtime.not.configured.error");
    }
    return null;
  }

  @Override
  public @NotNull ActionHandler getFixHandler(@NotNull Project project, @NotNull VirtualFile file) {
    return new ActionHandler() {
      @Override
      public void handlePanelActionClick(@NotNull EditorNotificationPanel panel, @NotNull HyperlinkEvent event) {
        doFix(project, file, handler -> handler.handlePanelActionClick(panel, event));
      }

      @Override
      public void handleQuickFixClick(@NotNull Editor editor, @NotNull PsiFile psiFile) {
        doFix(project, file, handler -> handler.handleQuickFixClick(editor, psiFile));
      }
    };
  }

  private static void doFix(@NotNull Project project,
                            @NotNull VirtualFile file,
                            @NotNull Consumer<ActionHandler> action) {
    final String javaErrorMessage = JavaProjectSdkSetupValidator.INSTANCE.getErrorMessage(project, file);
    if (javaErrorMessage != null) {
      action.consume(JavaProjectSdkSetupValidator.INSTANCE.getFixHandler(project, file));
      return;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    final String moduleName = module != null && !module.isDisposed() ? module.getName() : null;
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(moduleName, ClasspathEditor.getName());
  }
}
