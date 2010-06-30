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

package org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.imports;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

/**
 * @author ilyas
 */
public class GrImportStatementImpl extends GroovyPsiElementImpl implements GrImportStatement {

  public GrImportStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitImportStatement(this);
  }

  public String toString() {
    return "Import statement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState _state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (PsiTreeUtil.isAncestor(this, place, false)) {
      return true;
    }

    ResolveState state = _state.put(ResolverProcessor.RESOLVE_CONTEXT, this);
    if (isOnDemand()) {
      if (!processDeclarationsForMultipleElements(processor, lastParent, place, state)) return false;
    }
    else {
      if (!processDeclarationsForSingleElement(processor, state)) return false;
    }

    return true;
  }

  private boolean processDeclarationsForSingleElement(PsiScopeProcessor processor, ResolveState state) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    String name = getImportedName();
    if (name == null) return true;

    NameHint nameHint = processor.getHint(NameHint.KEY);

    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    String qName = ref.getCanonicalText();
    if (qName.indexOf('.') <= 0) return true;

    if (isStatic()) {
      final int i = qName.lastIndexOf('.');
      if (i > 0) {
        final String classQName = qName.substring(0, i);
        PsiClass clazz = facade.findClass(classQName, getResolveScope());
        if (clazz != null) {
          final String refName = ref.getReferenceName();
          if (nameHint == null || name.equals(nameHint.getName(state))) {
            final PsiField field = clazz.findFieldByName(refName, false);
            if (field != null && field.hasModifierProperty(PsiModifier.STATIC)) {
              if (!processor.execute(field, state)) return false;
            }

            for (PsiMethod method : clazz.findMethodsByName(refName, false)) {
              if (method.hasModifierProperty(PsiModifier.STATIC)) {
                if (!processor.execute(method, state)) return false;
              }
            }
          }

          final PsiMethod getter = GroovyPropertyUtils.findPropertyGetter(clazz, refName, true, true);
          if (getter != null &&
              (nameHint == null || name.equals(GroovyPropertyUtils.getPropertyNameByGetterName(nameHint.getName(state), true)))) {
            if (!processor.execute(getter, state)) return false;
          }

          final PsiMethod setter = GroovyPropertyUtils.findPropertySetter(clazz, refName, true, true);
          if (setter != null &&
              (nameHint == null || name.equals(GroovyPropertyUtils.getPropertyNameBySetterName(nameHint.getName(state))))) {
            if (!processor.execute(setter, state)) return false;
          }
        }
      }
    }
    else {
      if (nameHint == null || name.equals(nameHint.getName(state))) {
        PsiClass clazz = facade.findClass(qName, getResolveScope());
        if (clazz != null) {
          if (!processor.execute(clazz, state)) return false;
        }
      }
    }
    return true;
  }

  private boolean processDeclarationsForMultipleElements(PsiScopeProcessor processor,
                                                         PsiElement lastParent,
                                                         PsiElement place,
                                                         ResolveState state) {
    GrCodeReferenceElement ref = getImportReference();
    if (ref == null) return true;

    if (isStatic()) {
      final PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        final PsiClass clazz = (PsiClass)resolved;
        if (!processAllMembers(processor, clazz)) return false;
      }
    }
    else {
      String qName = PsiUtil.getQualifiedReferenceText(ref);
      if (qName != null) {
        PsiPackage aPackage = JavaPsiFacade.getInstance(getProject()).findPackage(qName);
        if (aPackage != null) {
          if (!aPackage.processDeclarations(processor, state, lastParent, place)) return false;
        }
      }
    }
    return true;
  }

  private static boolean processAllMembers(PsiScopeProcessor processor, PsiClass clazz) {
    for (PsiField field : clazz.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC) && !ResolveUtil.processElement(processor, field)) return false;
    }
    for (PsiMethod method : clazz.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.STATIC) && !ResolveUtil.processElement(processor, method)) return false;
    }
    return true;
  }

  public GrCodeReferenceElement getImportReference() {
    return (GrCodeReferenceElement)findChildByType(GroovyElementTypes.REFERENCE_ELEMENT);
  }

  @Nullable
  public String getImportedName() {
    if (isOnDemand()) return null;

    PsiElement identifier = findChildByType(GroovyTokenTypes.mIDENT);
    //this was aliased import
    if (identifier != null) {
      return identifier.getText();
    }

    GrCodeReferenceElement ref = getImportReference();
    return ref == null ? null : ref.getReferenceName();
  }

  public boolean isStatic() {
    return findChildByType(GroovyTokenTypes.kSTATIC) != null;
  }

  public boolean isAliasedImport() {
    return findChildByType(GroovyTokenTypes.mIDENT) != null;
  }

  public boolean isOnDemand() {
    return findChildByType(GroovyTokenTypes.mSTAR) != null;
  }

  public GrModifierList getAnnotationList() {
    return findChildByClass(GrModifierList.class);
  }
}
