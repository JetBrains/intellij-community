/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public abstract class GroovyConsoleImpl extends LanguageConsoleImpl {

  public GroovyConsoleImpl(Project project, String name) {
    super(project, name, GroovyLanguage.INSTANCE);
  }

  @NotNull
  @Override
  protected PsiFile createFile(@NotNull Project project,
                               @NotNull VirtualFile virtualFile) {
    return new GroovyShellCodeFragment(project, (LightVirtualFile)virtualFile, isShell());
  }

  protected abstract boolean isShell();

  protected void processCode() {
    for (GrTopStatement statement : getFile().getTopStatements()) {
      if (statement instanceof GrImportStatement) {
        getFile().addImportsFromString(importToString((GrImportStatement)statement));
      }
      else if (statement instanceof GrMethod) {
        getFile().addVariable(((GrMethod)statement).getName(), generateClosure((GrMethod)statement));
      }
      else if (statement instanceof GrAssignmentExpression) {
        GrAssignmentExpression assignment = (GrAssignmentExpression)statement;
        GrExpression left = assignment.getLValue();
        if (left instanceof GrReferenceExpression && !((GrReferenceExpression)left).isQualified()) {
          getFile().addVariable(((GrReferenceExpression)left).getReferenceName(), assignment.getRValue());
        }
      }
      else if (statement instanceof GrTypeDefinition) {
        getFile().addTypeDefinition(prepareTypeDefinition((GrTypeDefinition)statement));
      }
    }

    PsiType scriptType = getFile().getInferredScriptReturnType();
    if (scriptType != null) {
      getFile().addVariable("_", scriptType);
    }
  }

  @Override
  @NotNull
  public GroovyShellCodeFragment getFile() {
    return (GroovyShellCodeFragment)myFile;
  }

  @NotNull
  private GrTypeDefinition prepareTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    GroovyFile file = factory.createGroovyFile("", false, myFile);
    return (GrTypeDefinition)file.add(typeDefinition);
  }

  @NotNull
  private GrClosableBlock generateClosure(@NotNull GrMethod method) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    StringBuilder buffer = new StringBuilder();

    buffer.append('{');
    GrParameter[] parameters = method.getParameters();
    for (GrParameter parameter : parameters) {
      buffer.append(parameter.getText());
      buffer.append(',');
    }
    if (parameters.length > 0) buffer.delete(buffer.length() - 1, buffer.length());
    buffer.append("->}");

    return factory.createClosureFromText(buffer.toString(), myFile);
  }

  @Nullable
  private static String importToString(@NotNull GrImportStatement anImport) {
    StringBuilder buffer = new StringBuilder();

    GrCodeReferenceElement reference = anImport.getImportReference();
    if (reference == null) return null;
    String qname = reference.getClassNameText();
    buffer.append(qname);
    if (!anImport.isOnDemand()) {
      String importedName = anImport.getImportedName();
      buffer.append(":").append(importedName);
    }

    return buffer.toString();
  }
}
