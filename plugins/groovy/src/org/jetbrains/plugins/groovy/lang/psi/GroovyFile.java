/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

/**
 * @author ven
 */
public interface GroovyFile extends PsiFile, GroovyPsiElement, GrVariableDeclarationOwner {
  String SCRIPT_BASE_CLASS_NAME = "groovy.lang.Script";
  
  GrTypeDefinition[] getTypeDefinitions();

  GrTopLevelDefintion[] getTopLevelDefinitions();

  @NotNull
  String getPackageName();

  GrPackageDefinition getPackageDefinition();

  GrTopStatement[] getTopStatements();

  GrImportStatement[] getImportStatements();

  GrImportStatement addImportForClass(PsiClass aClass);
  void removeImport(GrImportStatement importStatement) throws IncorrectOperationException;
  GrStatement addStatement(GrStatement statement, GrStatement anchor) throws IncorrectOperationException;

  boolean isScript();

  @Nullable
  PsiClass getScriptClass();

  void setPackageDefinition(String packageName);
}
