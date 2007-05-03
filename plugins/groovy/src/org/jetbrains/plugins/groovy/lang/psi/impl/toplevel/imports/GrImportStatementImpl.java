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

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.lang.ASTNode;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeOrPackageReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author ilyas
 */
public class GrImportStatementImpl extends GroovyPsiElementImpl implements GrImportStatement {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Import statement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    if (isOnDemand()) {
      GrTypeOrPackageReferenceElement ref = getImportReference();
      if (ref != null) {
        String qName = PsiUtil.getQualifiedReferenceText(ref);
        if (qName != null) {
          PsiPackage aPackage = getManager().findPackage(qName);
          if (aPackage != null) {
            if (!aPackage.processDeclarations(processor, substitutor, lastParent, place)) return false;
          }
        }
      }
    } else {
      String name = getImportedName();
      if (name != null) {
        NameHint nameHint = processor.getHint(NameHint.class);
        if (nameHint == null || name.equals(nameHint.getName())) {
          GrTypeOrPackageReferenceElement ref = getImportReference();
          if (ref != null) {
            String qName = PsiUtil.getQualifiedReferenceText(ref);
            if (qName!= null) {
              PsiClass clazz = getManager().findClass(qName, getResolveScope());
              if (clazz != null && !processor.execute(clazz, substitutor)) return false;
            }
          }
        }
      }
    }

    return true;
  }

  public GrTypeOrPackageReferenceElement getImportReference() {
    return findChildByClass(GrTypeOrPackageReferenceElement.class);
  }

  public String getImportedName() {
    PsiElement identifier = findChildByType(GroovyTokenTypes.mIDENT);
    //this was aliased import
    if (identifier != null) {
      return identifier.getText();
    }

    GrTypeOrPackageReferenceElement ref = findChildByClass(GrTypeOrPackageReferenceElement.class);
    return ref == null ? null : ref.getReferenceName();
  }

  public boolean isOnDemand() {
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }


}
