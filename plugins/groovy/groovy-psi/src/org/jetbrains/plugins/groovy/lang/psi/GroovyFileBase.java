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

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportHolder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

/**
 * @author ilyas
 */
public interface GroovyFileBase extends PsiFile, GrVariableDeclarationOwner, GrStatementOwner, PsiClassOwner, GrControlFlowOwner, PsiImportHolder,
                                        GrDeclarationHolder {
  String[] IMPLICITLY_IMPORTED_PACKAGES = {
      "java.lang",
      "java.util",
      "java.io",
      "java.net",
      "groovy.lang",
      "groovy.util",
  };
  String[] IMPLICITLY_IMPORTED_CLASSES = {
      "java.math.BigInteger",
      "java.math.BigDecimal",
  };

  @NotNull
  GrTypeDefinition[] getTypeDefinitions();

  @NotNull
  GrTopLevelDefinition[] getTopLevelDefinitions();

  @NotNull
  GrMethod[] getCodeMethods();

  @NotNull
  GrMethod[] getMethods();

  @NotNull
  GrTopStatement[] getTopStatements();

  @Nullable
  GrImportStatement addImportForClass(@NotNull PsiClass aClass) throws IncorrectOperationException;

  void removeImport(@NotNull GrImportStatement importStatement) throws IncorrectOperationException;

  @NotNull
  GrImportStatement addImport(@NotNull GrImportStatement statement) throws IncorrectOperationException;

  boolean isScript();

  @Nullable
  PsiClass getScriptClass();
}
