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
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrReferenceAdjuster {
  private GrReferenceAdjuster() {
  }


  public static boolean shortenReferences(PsiElement element) {
    final TextRange range = element.getTextRange();
    return shortenReferences(element, range.getStartOffset(), range.getEndOffset(), true, false);
  }

  public static boolean shortenReferences(PsiElement element, int start, int end, boolean addImports, boolean incomplete) {
    return process(element, start, end, addImports, incomplete);
  }

  public static <T extends PsiElement> boolean shortenReference(GrQualifiedReference<T> ref) {
    boolean result = shortenReferenceInner(ref, true, false);
    final TextRange range = ref.getTextRange();
    result |= process(ref, range.getStartOffset(), range.getEndOffset(), true, false);
    return result;
  }

  private static boolean process(PsiElement element, int start, int end, boolean addImports, boolean incomplete) {
    boolean result = false;
    if (element instanceof GrQualifiedReference<?> && ((GrQualifiedReference)element).resolve() instanceof PsiClass) {
      result = shortenReferenceInner((GrQualifiedReference<?>)element, addImports, incomplete);
    }
    else if (element instanceof GrReferenceExpression && PsiUtil.isSuperReference(((GrReferenceExpression)element).getQualifier())) {
      result = shortenReferenceInner((GrReferenceExpression)element, addImports, incomplete);
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      final TextRange range = child.getTextRange();
      if (start < range.getEndOffset() && range.getStartOffset() < end) {
        result |= process(child, start, end, addImports, incomplete);
      }
      child = child.getNextSibling();
    }
    return result;
  }

  private static <Qualifier extends PsiElement> boolean shortenReferenceInner(GrQualifiedReference<Qualifier> ref, boolean addImports, boolean incomplete) {

    final Qualifier qualifier = ref.getQualifier();
    if (qualifier == null || PsiUtil.isSuperReference(qualifier) || cannotShortenInContext(ref)) {
      return false;
    }

    if (ref instanceof GrReferenceExpression) {
      final GrTypeArgumentList typeArgs = ((GrReferenceExpression)ref).getTypeArgumentList();
      if (typeArgs != null && typeArgs.getTypeArgumentElements().length > 0) {
        return false;
      }
    }

    if (!shorteningIsMeaningfully(ref)) return false;

    final PsiElement resolved = resolveRef(ref, incomplete);
    if (resolved == null) return false;

    if (!checkCopyWithoutQualifier(ref, addImports, resolved)) return false;
    ref.setQualifier(null);
    return true;
  }

  private static <Qualifier extends PsiElement> boolean checkCopyWithoutQualifier(GrQualifiedReference<Qualifier> ref,
                                                                                  boolean addImports,
                                                                                  PsiElement resolved) {
    final GrQualifiedReference<Qualifier> copy = getCopy(ref);
    copy.setQualifier(null);

    if (copy.isReferenceTo(resolved)) return true;

    if (resolved instanceof PsiClass) {
      final PsiClass clazz = (PsiClass)resolved;
      final String qName = clazz.getQualifiedName();
      if (qName != null && addImports && checkIsInnerClass(clazz) && mayInsertImport(ref)) {
        final GroovyFileBase file = (GroovyFileBase)ref.getContainingFile();
        final GrImportStatement added = file.addImportForClass(clazz);
        if (copy.isReferenceTo(resolved)) return true;
        file.removeImport(added);
      }
    }

    return false;
  }

  private static boolean checkIsInnerClass(PsiClass resolved) {
    final PsiClass containingClass = resolved.getContainingClass();
    return containingClass == null || CodeStyleSettingsManager.getSettings(resolved.getProject())
      .getCustomSettings(GroovyCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS;
  }

  @Nullable
  private static <Qualifier extends PsiElement> PsiElement resolveRef(GrQualifiedReference<Qualifier> ref, boolean incomplete) {
    if (!incomplete) return ref.resolve();

    PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();
    if (ref instanceof GrReferenceElement) {
      final String classNameText = ((GrReferenceElement)ref).getClassNameText();
      if (classNameText != null) {
        return helper.resolveReferencedClass(classNameText, ref);
      }
    }
    return null;
  }


  @SuppressWarnings("unchecked")
  private static <Qualifier extends PsiElement> GrQualifiedReference<Qualifier> getCopy(GrQualifiedReference<Qualifier> ref) {
    if (ref.getParent() instanceof GrMethodCall) {
      final GrMethodCall copy = ((GrMethodCall)ref.getParent().copy());
      return (GrQualifiedReference<Qualifier>)copy.getInvokedExpression();
    }
    return (GrQualifiedReference<Qualifier>)ref.copy();
  }

  private static <Qualifier extends PsiElement> boolean shorteningIsMeaningfully(GrQualifiedReference<Qualifier> ref) {

    if (ref instanceof GrReferenceElementImpl && ((GrReferenceElementImpl)ref).isFullyQualified()) {
      final GrDocComment doc = PsiTreeUtil.getParentOfType(ref, GrDocComment.class);
      if (doc != null) {
        if (CodeStyleSettingsManager.getSettings(ref.getProject()).getCustomSettings(GroovyCodeStyleSettings.class).USE_FQ_CLASS_NAMES_IN_JAVADOC) return false;
      }
      else {
        if (CodeStyleSettingsManager.getSettings(ref.getProject()).getCustomSettings(GroovyCodeStyleSettings.class).USE_FQ_CLASS_NAMES) return false;
      }
    }

    final Qualifier qualifier = ref.getQualifier();

    if (qualifier instanceof GrCodeReferenceElement) {
      return true;
    }

    if (qualifier instanceof GrExpression) {
      if (qualifier instanceof GrReferenceExpression && PsiUtil.isThisReference(qualifier)) return true;
      if (qualifier instanceof GrReferenceExpression && seemsToBeQualifiedClassName((GrExpression)qualifier)) {
        final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass || resolved instanceof PsiPackage) return true;
      }
    }
    return false;
  }

  private static <Qualifier extends PsiElement> boolean cannotShortenInContext(GrQualifiedReference<Qualifier> ref) {
    return PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) != null ||
           PsiTreeUtil.getParentOfType(ref, GroovyCodeFragment.class) != null;
  }

  private static <Qualifier extends PsiElement> boolean mayInsertImport(GrQualifiedReference<Qualifier> ref) {
    return !(ref.getContainingFile() instanceof GroovyCodeFragment) &&
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) == null &&
           ref.getContainingFile() instanceof GroovyFileBase;
  }

  public static boolean seemsToBeQualifiedClassName(@Nullable GrExpression expr) {
    if (expr == null) return false;
    while (expr instanceof GrReferenceExpression) {
      final PsiElement nameElement = ((GrReferenceExpression)expr).getReferenceNameElement();
      if (((GrReferenceExpression)expr).getTypeArguments().length > 0) return false;
      if (nameElement == null || nameElement.getNode().getElementType() != GroovyTokenTypes.mIDENT) return false;
      IElementType dotType = ((GrReferenceExpression)expr).getDotTokenType();
      if (dotType != null && dotType != GroovyTokenTypes.mDOT) return false;
      expr = ((GrReferenceExpression)expr).getQualifierExpression();
    }
    return expr == null;
  }
}
