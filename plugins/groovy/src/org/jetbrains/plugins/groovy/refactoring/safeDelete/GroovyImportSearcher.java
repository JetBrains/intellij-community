/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.safeDelete.ImportSearcher;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Max Medvedev
 */
public class GroovyImportSearcher extends ImportSearcher {
  @Override
  public PsiElement findImport(PsiElement element, boolean onlyNonStatic) {
    PsiFile file = element.getContainingFile();
    if (file instanceof GroovyFile) {
      GrImportStatement anImport = PsiTreeUtil.getParentOfType(element, GrImportStatement.class);
      if (!(anImport == null || anImport.isStatic() && onlyNonStatic)) {
        return anImport;
      }
    }
    return null;
  }
}
