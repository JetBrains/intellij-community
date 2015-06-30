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
public class GroovyStubNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  static final String GROOVY_STUBS = "groovyStubs";
  private static final Key<EditorNotificationPanel> KEY = Key.create("GroovyStubNotificationProvider");
  private final Project myProject;

  public GroovyStubNotificationProvider(Project project) {
    myProject = project;
  }

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
    panel.createActionLabel("Go to the Groovy class", new Runnable() {
      @Override
      public void run() {
        DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
          @Override
          public void run() {
            final PsiClass original = findClassByStub(project, file);
            if (original != null) {
              original.navigate(true);
            }
          }
        });
      }
    });
    panel.createActionLabel("Exclude from stub generation", new Runnable() {
      @Override
      public void run() {
        DumbService.getInstance(project).withAlternativeResolveEnabled(new Runnable() {
          @Override
          public void run() {
            final PsiClass psiClass = findClassByStub(project, file);
            if (psiClass != null) {
              ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
            }
          }
        });
      }
    });
    return panel;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getName().endsWith(".java") && file.getPath().contains(GROOVY_STUBS)) {
      final PsiClass psiClass = findClassByStub(myProject, file);
      if (psiClass != null) {
        return decorateStubFile(file, myProject);
      }
    }

    return null;
  }
}
