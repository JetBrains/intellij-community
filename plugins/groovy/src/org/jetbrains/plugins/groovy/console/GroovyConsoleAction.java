/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;

/**
 * Created by Max Medvedev on 9/20/13
 */
public class GroovyConsoleAction extends GroovyShellActionBase {
  @Override
  protected GroovyShellRunner getRunner(Module module) {
    return GroovyShellRunner.getAppropriateRunner(module);
  }

  @Override
  public String getTitle() {
    return "Groovy Console";
  }

  @Override
  protected LanguageConsoleImpl createConsole(Project project, String title) {
    return new LanguageConsoleImpl(project, title, GroovyFileType.GROOVY_LANGUAGE) {
      @NotNull
      @Override
      protected PsiFile createFile(@NotNull LightVirtualFile virtualFile, @NotNull Document document, @NotNull Project project) {
        return new GroovyCodeFragment(getProject(), virtualFile);
      }
    };
  }
}
