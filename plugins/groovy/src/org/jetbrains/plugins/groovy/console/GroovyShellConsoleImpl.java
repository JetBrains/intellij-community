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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Max Medvedev
 */
public class GroovyShellConsoleImpl extends LanguageConsoleImpl {
  public GroovyShellConsoleImpl(Project project, String name) {
    super(project, name, GroovyFileType.GROOVY_LANGUAGE);
  }

  @NotNull
  @Override
  protected PsiFile createFile(@NotNull LightVirtualFile virtualFile, @NotNull Document document, @NotNull Project project) {
    return new GroovyCodeFragment(project, virtualFile);
  }

  @NotNull
  @Override
  protected String addToHistoryInner(TextRange textRange, EditorEx editor, boolean erase, boolean preserveMarkup) {
    final String result = super.addToHistoryInner(textRange, editor, erase, preserveMarkup);

    if (result.startsWith("import")) {
      String prepared = prepareImport(result);
      if (prepared != null) {
        ((GroovyCodeFragment)myFile).addImportsFromString(prepared);
      }
    }

    return result;
  }

  @Nullable
  private String prepareImport(String rawImport) {
    try {
      GrImportStatement anImport = GroovyPsiElementFactory.getInstance(getProject()).createImportStatementFromText(rawImport);
      StringBuilder buffer = new StringBuilder();

      String qname = anImport.getImportReference().getClassNameText();
      buffer.append(qname);
      if (!anImport.isOnDemand()) {
        String importedName = anImport.getImportedName();
        buffer.append(":").append(importedName);
      }

      return buffer.toString();
    }
    catch (IncorrectOperationException ignored) {
      return null;
    }
  }
}
