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

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.Arrays;

/**
 * @author ilyas
 */
public class GroovyCompilerLoader extends AbstractProjectComponent {
  static final String GROOVY_STUBS = "groovyStubs";
  private final CompilerManager myCompilerManager;
  private final FileEditorManager myFileEditorManager;

  public GroovyCompilerLoader(Project project, CompilerManager manager, FileEditorManager editorManager) {
    super(project);
    myCompilerManager = manager;
    myFileEditorManager = editorManager;
  }

  @Nullable
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

  @Override
  public void projectOpened() {
    myCompilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull final VirtualFile file) {
        if (file.getName().endsWith(".java") && file.getPath().contains(GROOVY_STUBS)) {
          final PsiClass psiClass = findClassByStub(myProject, file);
          if (psiClass != null) {
            final FileEditor[] editors = myFileEditorManager.getEditors(file);
            if (editors.length != 0) {
              decorateStubFile(file, myFileEditorManager, editors[0]);
            }
          }
        }
      }
    });
  }

  private void decorateStubFile(final VirtualFile file, FileEditorManager fileEditorManager, FileEditor editor) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("This stub is generated for Groovy class to make Groovy-Java cross-compilation possible");
    panel.createActionLabel("Go to the Groovy class", new Runnable() {
      @Override
      public void run() {
        final PsiClass original = findClassByStub(myProject, file);
        if (original != null) {
          original.navigate(true);
        }
      }
    });
    panel.createActionLabel("Exclude from stub generation", new Runnable() {
      @Override
      public void run() {
        final PsiClass psiClass = findClassByStub(myProject, file);
        if (psiClass != null) {
          ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
        }
      }
    });
    fileEditorManager.addTopComponent(editor, panel);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "GroovyCompilerLoader";
  }
}
