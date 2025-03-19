// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.util.Arrays;
import java.util.function.Function;

public final class GroovyStubNotificationProvider implements EditorNotificationProvider {
  static final String GROOVY_STUBS = "groovyStubs";

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    if (!file.getName().endsWith(".java") || !file.getPath().contains(GROOVY_STUBS)) return null;
    final PsiClass psiClass = findClassByStub(project, file);
    if (psiClass == null) return null;

    return fileEditor -> decorateStubFile(file, project, fileEditor);
  }

  @VisibleForTesting
  public static @Nullable PsiClass findClassByStub(Project project, VirtualFile stubFile) {
    final String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
    final int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
    if (stubs < 0 || stubs >= components.length - 3) {
      return null;
    }

    final String moduleName = components[stubs + 1];
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module == null) {
      return null;
    }

    final String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 3, components.length), ".");
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
  }

  private static EditorNotificationPanel decorateStubFile(final VirtualFile file, final Project project, @NotNull FileEditor fileEditor) {
    final EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info);
    panel.setText(GroovyBundle.message("generated.stub.message"));
    panel.createActionLabel(GroovyBundle.message("generated.stub.navigate.link.label"), () -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass original = findClassByStub(project, file);
      if (original != null) {
        original.navigate(true);
      }
    }));
    panel.createActionLabel(GroovyBundle.message("generated.stub.exclude.link.label"), () -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass psiClass = findClassByStub(project, file);
      if (psiClass != null) {
        ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
      }
    }));
    return panel;
  }
}
