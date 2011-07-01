/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;

/**
 * @author Max Medvedev
 */
public class GrReferenceAdjuster {
  private GrReferenceAdjuster() {
  }


  public static void shortenReferences(PsiElement element) {
    final TextRange range = element.getTextRange();
    shortenReferences(element, range.getStartOffset(), range.getEndOffset(), true, false);
  }

  public static void shortenReferences(PsiElement element, int start, int end, boolean addImports, boolean uncomplete) {
    process(element, start, end, addImports, uncomplete);
  }

  public static void shortenReference(GrQualifiedReference ref) {
    shortenReferenceInner(ref, true, false);
    final TextRange range = ref.getTextRange();
    process(ref, range.getStartOffset(), range.getEndOffset(), true, false);
  }

  private static void process(PsiElement element, int start, int end, boolean addImports, boolean uncomplete) {
    if (element instanceof GrQualifiedReference && ((GrQualifiedReference)element).resolve() instanceof PsiClass) {
      shortenReferenceInner((GrQualifiedReference)element, addImports, uncomplete);
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      final TextRange range = child.getTextRange();
      if (start < range.getEndOffset() && range.getStartOffset() < end) {
        process(child, start, end, addImports, uncomplete);
      }
      child = child.getNextSibling();
    }
  }

  private static <Qualifier extends PsiElement> boolean shortenReferenceInner(GrQualifiedReference<Qualifier> ref, boolean addImports, boolean uncomplete) {

    final Qualifier qualifier = ref.getQualifier();
    if (qualifier == null || qualifier instanceof GrSuperReferenceExpression || cannotShortenInContext(ref)) {
      return false;
    }

    if (ref instanceof GrReferenceExpression) {
      final GrTypeArgumentList typeArgs = ((GrReferenceExpression)ref).getTypeArgumentList();
      if (typeArgs != null && typeArgs.getTypeArgumentElements().length > 0) {
        return false;
      }
    }

    if (!shorteningIsMeaningfully(ref)) return false;

    final PsiElement resolved = resolveRef(ref, uncomplete);
    if (resolved == null) return false;

    if (!CodeStyleSettingsManager.getSettings(ref.getProject()).INSERT_INNER_CLASS_IMPORTS && resolved instanceof PsiClass && ((PsiClass)resolved).getContainingClass() != null) {
      return false;
    }

    final GrQualifiedReference<Qualifier> copy = getCopy(ref);

    copy.setQualifier(null);
    if (!copy.isReferenceTo(resolved)) {
      if (resolved instanceof PsiClass) {
        final GroovyFileBase file = (GroovyFileBase)ref.getContainingFile();
        final PsiClass clazz = (PsiClass)resolved;
        final String qName = clazz.getQualifiedName();
        if (qName != null) {
          if (addImports && mayInsertImport(ref)) {
            final GrImportStatement added = file.addImportForClass(clazz);
            if (!copy.isReferenceTo(resolved)) {
              file.removeImport(added);
              return false;
            }
          }
        }
      }
      else {
        return false;
      }
    }
    ref.setQualifier(null);
    return true;
  }

  @Nullable
  private static <Qualifier extends PsiElement> PsiElement resolveRef(GrQualifiedReference<Qualifier> ref, boolean uncomplete) {
    if (!uncomplete) return ref.resolve();

    PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();
    if (ref instanceof GrReferenceElement) {
      final String classNameText = ((GrReferenceElement)ref).getClassNameText();
      if (classNameText != null) {
        return helper.resolveReferencedClass(classNameText, ref);
      }
    }
    return null;
  }


  private static <Qualifier extends PsiElement> GrQualifiedReference<Qualifier> getCopy(GrQualifiedReference<Qualifier> ref) {
    if (ref.getParent() instanceof GrMethodCall) {
      final GrMethodCall copy = ((GrMethodCall)ref.getParent().copy());
      return (GrQualifiedReference<Qualifier>)copy.getInvokedExpression();
    }
    return (GrQualifiedReference<Qualifier>)ref.copy();
  }

  private static <Qualifier extends PsiElement> boolean shorteningIsMeaningfully(GrQualifiedReference<Qualifier> ref) {

    if (ref instanceof GrReferenceElementImpl) {
      if (((GrReferenceElementImpl)ref).isFullyQualified() && CodeStyleSettingsManager.getSettings(ref.getProject()).USE_FQ_CLASS_NAMES) return false;
    }

    final Qualifier qualifier = ref.getQualifier();

    if (qualifier instanceof GrCodeReferenceElement) {
      return true;
    }

    if (qualifier instanceof GrExpression) {
      if (qualifier instanceof GrThisReferenceExpression) return true;
      if (qualifier instanceof GrReferenceExpression && seemsToBeQualifiedClassName((GrExpression)qualifier)) {
        final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass || resolved instanceof PsiPackage) return true;
      }
    }
    return false;
  }

  private static <Qualifier extends PsiElement> boolean cannotShortenInContext(GrQualifiedReference<Qualifier> ref) {
    return (PsiTreeUtil.getParentOfType(ref, GrDocMemberReference.class) == null &&
            PsiTreeUtil.getParentOfType(ref, GrDocComment.class) != null) ||
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) != null ||
           PsiTreeUtil.getParentOfType(ref, GroovyCodeFragment.class) != null;
  }

  private static <Qualifier extends PsiElement> boolean mayInsertImport(GrQualifiedReference<Qualifier> ref) {
    return PsiTreeUtil.getParentOfType(ref, GrDocComment.class) == null &&
           !(ref.getContainingFile() instanceof GroovyCodeFragment) &&
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) == null;
  }

  private static boolean seemsToBeQualifiedClassName(@Nullable GrExpression qualifier) {
    if (qualifier == null) return false;
    while (qualifier instanceof GrReferenceExpression) {
      final PsiElement nameElement = ((GrReferenceExpression)qualifier).getReferenceNameElement();
      if (((GrReferenceExpression)qualifier).getTypeArguments().length > 0) return false;
      if (nameElement == null || nameElement.getNode().getElementType() != GroovyTokenTypes.mIDENT) return false;
      IElementType dotType = ((GrReferenceExpression)qualifier).getDotTokenType();
      if (dotType != null && dotType != GroovyTokenTypes.mDOT) return false;
      qualifier = ((GrReferenceExpression)qualifier).getQualifierExpression();
    }
    return qualifier == null;
  }
}
