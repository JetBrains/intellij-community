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
package org.jetbrains.plugins.groovy.editor;

import com.intellij.codeInsight.editorActions.CopyPasteReferenceProcessor;
import com.intellij.codeInsight.editorActions.ReferenceData;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

/**
 * @author peter
 */
public class GroovyReferenceCopyPasteProcessor extends CopyPasteReferenceProcessor<GrReferenceElement> {
  private static final Logger LOG = Logger.getInstance(GroovyReferenceCopyPasteProcessor.class);

  @Override
  protected void addReferenceData(PsiFile file, int startOffset, PsiElement element, ArrayList<ReferenceData> to) {
    if (element instanceof GrReferenceElement) {
      if (((GrReferenceElement)element).getQualifier() == null) {
        final GroovyResolveResult resolveResult = ((GrReferenceElement)element).advancedResolve();
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
  protected GrReferenceElement[] findReferencesToRestore(PsiFile file,
                                                         RangeMarker bounds,
                                                         ReferenceData[] referenceData) {
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

      if (element != null && element.getParent() instanceof GrReferenceElement && !PsiUtil.isThisOrSuperRef(element.getParent())) {
        GrReferenceElement reference = (GrReferenceElement)element.getParent();
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
  protected void restoreReferences(ReferenceData[] referenceData,
                                   GrReferenceElement[] refs) {
    for (int i = 0; i < refs.length; i++) {
      GrReferenceElement reference = refs[i];
      if (reference == null) continue;
      try {
        PsiManager manager = reference.getManager();
        ReferenceData refData = referenceData[i];
        PsiClass refClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(refData.qClassName, reference.getResolveScope());
        if (refClass != null) {
          if (refData.staticMemberName == null) {
            reference.bindToElement(refClass);
          }
          else {
            LOG.assertTrue(reference instanceof GrReferenceExpression);
            PsiMember member = findMember(refData, refClass);
            if (member != null) {
              ((GrReferenceExpression)reference).bindToElementViaStaticImport(member);
            }
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }


  @Nullable
  private static PsiMember findMember(ReferenceData refData, PsiClass refClass) {
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
