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

package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * @author peter
 */
public class GroovyAddImportAction extends ImportClassFixBase<GrReferenceElement, GrReferenceElement> {
  private final GrReferenceElement<?> ref;

  public GroovyAddImportAction(@NotNull GrReferenceElement ref) {
    super(ref, ref);
    this.ref = ref;
  }

  @Override
  protected String getReferenceName(@NotNull GrReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected PsiElement getReferenceNameElement(@NotNull GrReferenceElement reference) {
    return reference.getReferenceNameElement();
  }

  @Override
  protected boolean hasTypeParameters(@NotNull GrReferenceElement reference) {
    return reference.getTypeArguments().length > 0;
  }

  @Override
  protected String getQualifiedName(@NotNull GrReferenceElement reference) {
    return reference.getCanonicalText();
  }

  @Override
  protected boolean isQualified(@NotNull GrReferenceElement reference) {
    return reference.getQualifier() != null;
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(PsiFile psiFile, String name) {
    if (!(psiFile instanceof GroovyFile)) return false;
    final GrImportStatement[] importStatements = ((GroovyFile)psiFile).getImportStatements();
    for (GrImportStatement importStatement : importStatements) {
      final GrCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null || importReference.resolve() != null) {
        continue;
      }
      if (importStatement.isOnDemand() || Comparing.strEqual(importStatement.getImportedName(), name)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  protected List<PsiClass> filterByContext(@NotNull List<PsiClass> candidates, @NotNull GrReferenceElement ref) {
    PsiElement typeElement = ref.getParent();
    if (typeElement instanceof GrTypeElement) {
      PsiElement decl = typeElement.getParent();
      if (decl instanceof GrVariableDeclaration) {
        GrVariable[] vars = ((GrVariableDeclaration)decl).getVariables();
        if (vars.length == 1) {
          PsiExpression initializer = vars[0].getInitializer();
          if (initializer != null) {
            PsiType type = initializer.getType();
            if (type != null) {
              return filterAssignableFrom(type, candidates);
            }
          }
        }
      }
      if (decl instanceof GrParameter) {
        return filterBySuperMethods((PsiParameter)decl, candidates);
      }
    }

    return super.filterByContext(candidates, ref);
  }

  @Override
  protected String getRequiredMemberName(@NotNull GrReferenceElement reference) {
    if (reference.getParent() instanceof GrReferenceElement) {
      return ((GrReferenceElement)reference.getParent()).getReferenceName();
    }
    return super.getRequiredMemberName(reference);
  }

  @Override
  protected boolean isAccessible(@NotNull PsiMember member, @NotNull GrReferenceElement reference) {
    return true;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    List<PsiClass> imports =
      ContainerUtil.map(((GroovyFile)(ref.getContainingFile())).getImportStatements(), GrImportStatement::resolveTargetClass);
    for (PsiClass classToImport : getClassesToImport()) {
      if (!imports.contains(classToImport)) {
        return super.showHint(editor);
      }
    }
    return false;
  }

  @Override
  protected void bindReference(@NotNull PsiReference reference, @NotNull PsiClass targetClass) {
    PsiElement referredElement = reference.getElement();
    if (referredElement instanceof GrReferenceExpression && PsiUtil.isNewified(referredElement)) {
      handleNewifiedClass(referredElement, targetClass);
    }
    else {
      super.bindReference(reference, targetClass);
    }
  }

  private static void handleNewifiedClass(@NotNull PsiElement referredElement, @NotNull PsiClass targetClass) {
    PsiFile file = referredElement.getContainingFile();
    String qualifiedClassName = targetClass.getQualifiedName();
    if (file instanceof GroovyFile && qualifiedClassName != null) {
      ((GroovyFile)file).importClass(targetClass);
    }
  }
}
