/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.ui.EditorNotificationPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.compiler.generator.GroovycStubGenerator;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @author ilyas
 */
public class GroovyCompilerLoader extends AbstractProjectComponent {

  public GroovyCompilerLoader(Project project) {
    super(project);
  }

  public void projectOpened() {
    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addCompilableFileType(GroovyFileType.GROOVY_FILE_TYPE);

    compilerManager.addTranslatingCompiler(new GroovycStubGenerator(myProject),
                                           new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE)),
                                           new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA)));

    compilerManager.addTranslatingCompiler(new GroovyCompiler(myProject),
                                           new HashSet<FileType>(Arrays.asList(GroovyFileType.GROOVY_FILE_TYPE, StdFileTypes.CLASS)),
                                           new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)));

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      @Override
      public void fileOpened(FileEditorManager source, final VirtualFile file) {
        if (file.getName().endsWith(".java") && file.getPath().contains(GroovycStubGenerator.GROOVY_STUBS)) {
          final PsiClass psiClass = GroovycStubGenerator.findClassByStub(myProject, file);
          if (psiClass != null) {
            final FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
            final FileEditor[] editors = fileEditorManager.getEditors(file);
            if (editors.length != 0) {
              decorateStubFile(file, fileEditorManager, editors[0]);
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
        final PsiClass original = GroovycStubGenerator.findClassByStub(myProject, file);
        if (original != null) {
          original.navigate(true);
        }
      }
    });
    panel.createActionLabel("Exclude from stub generation", new Runnable() {
      @Override
      public void run() {
        final PsiClass psiClass = GroovycStubGenerator.findClassByStub(myProject, file);
        if (psiClass != null) {
          ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
        }
      }
    });
    fileEditorManager.addTopComponent(editor, panel);
  }

  @NotNull
  public String getComponentName() {
    return "GroovyCompilerLoader";
  }
}
