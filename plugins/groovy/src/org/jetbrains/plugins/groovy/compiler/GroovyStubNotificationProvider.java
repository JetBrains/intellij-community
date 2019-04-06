// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.compiler;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author peter
 */
public final class GroovyStubNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  static final String GROOVY_STUBS = "groovyStubs";
  private static final Key<EditorNotificationPanel> KEY = Key.create("GroovyStubNotificationProvider");

  @Nullable
  @VisibleForTesting
  public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
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

  private static EditorNotificationPanel decorateStubFile(final VirtualFile file, final Project project) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("This stub is generated for Groovy class to make Groovy-Java cross-compilation possible");
    panel.createActionLabel("Go to the Groovy class", () -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass original = findClassByStub(project, file);
      if (original != null) {
        original.navigate(true);
      }
    }));
    panel.createActionLabel("Exclude from stub generation", () -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass psiClass = findClassByStub(project, file);
      if (psiClass != null) {
        ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
      }
    }));
    return panel;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
    if (file.getName().endsWith(".java") && file.getPath().contains(GROOVY_STUBS)) {
      final PsiClass psiClass = findClassByStub(project, file);
      if (psiClass != null) {
        return decorateStubFile(file, project);
      }
    }

    return null;
  }
}
