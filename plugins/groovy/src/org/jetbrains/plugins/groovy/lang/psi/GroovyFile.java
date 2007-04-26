/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * Implements all abstractionos related to Groovy file
 *
 * @author Ilya Sergey
 */
public class GroovyFile extends PsiFileBase {

  public GroovyFile(FileViewProvider viewProvider) {
    super(viewProvider, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
  }

  @NotNull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  public GrTypeDefinition[] getTypeDefinitions() {
    return findChildrenByClass(GrTypeDefinition.class);
  }

  @NotNull
  public String getPackageName() {
    GrPackageDefinition packageDef = findChildByClass(GrPackageDefinition.class);
    if (packageDef != null) {
      return packageDef.getPackageName();
    }

    return "";
  }

  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  public GrImportStatement[] getImportStatements() {
    return findChildrenByClass(GrImportStatement.class);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    for (final GrImportStatement importStatement : getImportStatements()) {
      if (!importStatement.processDeclarations(processor, substitutor, lastParent, place)) return false;
    }

    //todo: process current and implicitly imported packages

    return true;
  }
}

