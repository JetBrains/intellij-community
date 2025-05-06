// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.editorActions.CopyPasteReferenceProcessor;
import com.intellij.codeInsight.editorActions.ReferenceData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class GroovyReferenceCopyPasteProcessor extends CopyPasteReferenceProcessor<GrReferenceElement> {
  private static final Logger LOG = Logger.getInstance(GroovyReferenceCopyPasteProcessor.class);

  @Override
  protected void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to) {
    if (DumbService.isDumb(element.getProject())) {
      // IDEA-284298
      return;
    }
    if (element instanceof GrReferenceElement) {
      if (((GrReferenceElement<?>)element).getQualifier() == null) {
        final GroovyResolveResult resolveResult = ((GrReferenceElement<?>)element).advancedResolve();
        final PsiElement refElement = resolveResult.getElement();
        if (refElement != null) {

          if (refElement instanceof PsiClass) {
            if (refElement.getContainingFile() != element.getContainingFile()) {
              final String qName = ((PsiClass)refElement).getQualifiedName();
              if (qName != null) {
                addReferenceData(element, to, startOffset, qName, null);
              }
            }
          }
          else if (resolveResult.getCurrentFileResolveContext() instanceof GrImportStatement &&
                   ((GrImportStatement)resolveResult.getCurrentFileResolveContext()).isStatic()) {
            final String classQName = ((PsiMember)refElement).getContainingClass().getQualifiedName();
            final String name = ((PsiNamedElement)refElement).getName();
            if (classQName != null && name != null) {
              addReferenceData(element, to, startOffset, classQName, name);
            }
          }
        }
      }
    }
  }

  @Override
  protected void removeImports(@NotNull PsiFile file, @NotNull Set<String> imports) {
    GroovyFile groovyFile = (GroovyFile)file;
    for (GrImportStatement statement : groovyFile.getImportStatements()) {
      if (imports.contains(statement.getImportedName())) {
        groovyFile.removeImport(statement);
      }
    }
  }


  @Override
  protected GrReferenceElement @NotNull [] findReferencesToRestore(@NotNull PsiFile file,
                                                                   @NotNull RangeMarker bounds,
                                                                   ReferenceData @NotNull [] referenceData) {
    PsiManager manager = file.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiResolveHelper helper = facade.getResolveHelper();
    GrReferenceElement[] refs = new GrReferenceElement[referenceData.length];
    for (int i = 0; i < referenceData.length; i++) {
      ReferenceData data = referenceData[i];

      PsiClass refClass = facade.findClass(data.qClassName, file.getResolveScope());
      if (refClass == null) continue;

      int startOffset = data.startOffset + bounds.getStartOffset();
      int endOffset = data.endOffset + bounds.getStartOffset();
      PsiElement element = file.findElementAt(startOffset);

      if (element != null && element.getParent() instanceof GrReferenceElement reference && !PsiUtil.isThisOrSuperRef(element.getParent())) {
        TextRange range = reference.getTextRange();
        if (range.getStartOffset() == startOffset && range.getEndOffset() == endOffset) {
          if (data.staticMemberName == null) {
            PsiClass refClass1 = helper.resolveReferencedClass(reference.getText(), reference);
            if (refClass1 == null || !manager.areElementsEquivalent(refClass, refClass1)) {
              refs[i] = reference;
            }
          }
          else {
            if (reference instanceof GrReferenceExpression) {
              PsiElement referent = resolveReferenceIgnoreOverriding(reference);
              if (!(referent instanceof PsiNamedElement)
                  || !data.staticMemberName.equals(((PsiNamedElement)referent).getName())
                  || !(referent instanceof PsiMember)
                  || ((PsiMember)referent).getContainingClass() == null
                  || !data.qClassName.equals(((PsiMember)referent).getContainingClass().getQualifiedName())) {
                refs[i] = reference;
              }
            }
          }
        }
      }
    }
    return refs;
  }

  @Override
  protected void restoreReferences(ReferenceData @NotNull [] referenceData,
                                   List<GrReferenceElement> refs,
                                   @NotNull Set<? super String> imported) {
    for (int i = 0; i < refs.size(); i++) {
      GrReferenceElement reference = refs.get(i);
      if (reference == null) continue;
      try {
        PsiManager manager = reference.getManager();
        ReferenceData refData = referenceData[i];
        PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(refData.qClassName, reference.getResolveScope());
        if (refClass != null) {
          if (refData.staticMemberName == null) {
            reference.bindToElement(refClass);
            imported.add(refData.qClassName);
          }
          else {
            LOG.assertTrue(reference instanceof GrReferenceExpression);
            PsiMember member = findMember(refData, refClass);
            if (member != null) {
              ((GrReferenceExpression)reference).bindToElementViaStaticImport(member);
              imported.add(StringUtil.getQualifiedName(refData.qClassName, refData.staticMemberName));
            }
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }


  private static @Nullable PsiMember findMember(ReferenceData refData, PsiClass refClass) {
    PsiField field = refClass.findFieldByName(refData.staticMemberName, true);
    if (field != null) {
      return field;
    }

    PsiMethod[] methods = refClass.findMethodsByName(refData.staticMemberName, true);
    if (methods.length != 0) {
      return methods[0];
    }

    return null;
  }
}
