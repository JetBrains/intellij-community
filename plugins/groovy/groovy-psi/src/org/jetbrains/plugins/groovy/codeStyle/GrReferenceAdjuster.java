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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.ReferenceAdjuster;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class GrReferenceAdjuster implements ReferenceAdjuster {
  public GrReferenceAdjuster() {
    @SuppressWarnings("UnusedDeclaration") int i = 0;
  }

  public static void shortenAllReferencesIn(@Nullable GroovyPsiElement newTypeElement) {
    if (newTypeElement != null) {
      newTypeElement.accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          super.visitCodeReferenceElement(refElement);
          shortenReference(refElement);
        }
      });
    }
  }

  @Override
  public ASTNode process(@NotNull ASTNode element, boolean addImports, boolean incompleteCode, boolean useFqInJavadoc, boolean useFqInCode) {
    final TextRange range = element.getTextRange();
    process(element.getPsi(), range.getStartOffset(), range.getEndOffset(), addImports, incompleteCode, useFqInJavadoc, useFqInCode);
    return element;
  }

  @Override
  public ASTNode process(@NotNull ASTNode element, boolean addImports, boolean incompleteCode, Project project) {
    GroovyCodeStyleSettingsFacade facade = GroovyCodeStyleSettingsFacade.getInstance(project);
    return process(element, addImports, incompleteCode, facade.useFqClassNamesInJavadoc(), facade.useFqClassNames());
  }

  @Override
  public void processRange(@NotNull ASTNode element, int startOffset, int endOffset, boolean useFqInJavadoc, boolean useFqInCode) {
    process(element.getPsi(), startOffset, endOffset, true, true, useFqInJavadoc, useFqInCode);
  }

  @Override
  public void processRange(@NotNull ASTNode element, int startOffset, int endOffset, Project project) {
    GroovyCodeStyleSettingsFacade facade = GroovyCodeStyleSettingsFacade.getInstance(project);
    processRange(element, startOffset, endOffset, facade.useFqClassNamesInJavadoc(), facade.useFqClassNames());
  }

  private static boolean process(@NotNull PsiElement element,
                                 int start,
                                 int end,
                                 boolean addImports,
                                 boolean incomplete,
                                 boolean useFqInJavadoc,
                                 boolean useFqInCode) {
    boolean result = false;
    if (element instanceof GrQualifiedReference<?> && ((GrQualifiedReference)element).resolve() instanceof PsiClass) {
      result = shortenReferenceInner((GrQualifiedReference<?>)element, addImports, incomplete, useFqInJavadoc, useFqInCode);
    }
    else if (element instanceof GrReferenceExpression && PsiUtil.isSuperReference(((GrReferenceExpression)element).getQualifier())) {
      result = shortenReferenceInner((GrReferenceExpression)element, addImports, incomplete, useFqInJavadoc, useFqInCode);
    }

    PsiElement child = element.getFirstChild();
    while (child != null) {
      final TextRange range = child.getTextRange();
      if (start < range.getEndOffset() && range.getStartOffset() < end) {
        result |= process(child, start, end, addImports, incomplete, useFqInJavadoc, useFqInCode);
      }
      child = child.getNextSibling();
    }
    return result;
  }

  public static <T extends PsiElement> boolean shortenReference(@NotNull GrQualifiedReference<T> ref) {
    GroovyCodeStyleSettingsFacade facade = GroovyCodeStyleSettingsFacade.getInstance(ref.getProject());
    boolean result = shortenReferenceInner(ref, true, false, facade.useFqClassNamesInJavadoc(), facade.useFqClassNames());
    final TextRange range = ref.getTextRange();
    result |= process(ref, range.getStartOffset(), range.getEndOffset(), true, false, facade.useFqClassNamesInJavadoc(), facade.useFqClassNames());
    return result;
  }

  private static <Qualifier extends PsiElement> boolean shortenReferenceInner(@NotNull GrQualifiedReference<Qualifier> ref,
                                                                              boolean addImports,
                                                                              boolean incomplete,
                                                                              boolean useFqInJavadoc,
                                                                              boolean useFqInCode) {

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

    if (!shorteningIsMeaningfully(ref, useFqInJavadoc, useFqInCode)) return false;

    final PsiElement resolved = resolveRef(ref, incomplete);
    if (resolved == null) return false;

    if (!checkCopyWithoutQualifier(ref, addImports, resolved)) return false;
    ref.setQualifier(null);
    return true;
  }

  private static <Qualifier extends PsiElement> boolean checkCopyWithoutQualifier(@NotNull GrQualifiedReference<Qualifier> ref,
                                                                                  boolean addImports,
                                                                                  @NotNull PsiElement resolved) {
    final GrQualifiedReference<Qualifier> copy = getCopy(ref);
    if (copy == null) return false;
    copy.setQualifier(null);

    final PsiElement resolvedCopy = copy.resolve();
    if (ref.getManager().areElementsEquivalent(resolved, resolvedCopy)) {
      return true;
    }
    else if (resolvedCopy != null && !(resolvedCopy instanceof GrBindingVariable) && !isFromDefaultPackage(resolvedCopy)) {
      return false;
    }

    if (resolved instanceof PsiClass) {
      final PsiClass clazz = (PsiClass)resolved;
      final String qName = clazz.getQualifiedName();
      if (qName != null && addImports && checkIsInnerClass(clazz, ref) && mayInsertImport(ref)) {
        final GroovyFileBase file = (GroovyFileBase)ref.getContainingFile();
        final GrImportStatement added = file.addImportForClass(clazz);
        if (added != null) {
          if (copy.isReferenceTo(resolved)) return true;
          file.removeImport(added);
        }
      }
    }

    return false;
  }

  private static boolean isFromDefaultPackage(@Nullable PsiElement element) {
    if (element instanceof PsiClass) {
      String qname = ((PsiClass)element).getQualifiedName();
      if (qname != null) {
        String packageName = StringUtil.getPackageName(qname);
        if (ArrayUtil.contains(packageName, GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES)) {
          return true;
        }
        if (ArrayUtil.contains(qname, GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES)) {
          return true;
        }
      }
    }

    return false;
  }

  private static <Qualifier extends PsiElement> boolean checkIsInnerClass(@NotNull PsiClass resolved, GrQualifiedReference<Qualifier> ref) {
    final PsiClass containingClass = resolved.getContainingClass();
    return containingClass == null ||
           PsiTreeUtil.isAncestor(containingClass, ref, true) ||
           GroovyCodeStyleSettingsFacade.getInstance(containingClass.getProject()).insertInnerClassImports();
  }

  @Nullable
  private static <Qualifier extends PsiElement> PsiElement resolveRef(@NotNull GrQualifiedReference<Qualifier> ref, boolean incomplete) {
    if (!incomplete) return ref.resolve();

    PsiResolveHelper helper = JavaPsiFacade.getInstance(ref.getProject()).getResolveHelper();
    if (ref instanceof GrReferenceElement) {
      final String classNameText = ((GrReferenceElement)ref).getClassNameText();
      return helper.resolveReferencedClass(classNameText, ref);
    }
    return null;
  }


  @SuppressWarnings("unchecked")
  @Nullable
  private static <Qualifier extends PsiElement> GrQualifiedReference<Qualifier> getCopy(@NotNull GrQualifiedReference<Qualifier> ref) {
    if (ref.getParent() instanceof GrMethodCall) {
      final GrMethodCall copy = ((GrMethodCall)ref.getParent().copy());
      return (GrQualifiedReference<Qualifier>)copy.getInvokedExpression();
    }
    return (GrQualifiedReference<Qualifier>)ref.copy();
  }

  private static <Qualifier extends PsiElement> boolean shorteningIsMeaningfully(@NotNull GrQualifiedReference<Qualifier> ref,
                                                                                 boolean useFqInJavadoc, boolean useFqInCode) {

    if (ref instanceof GrReferenceElementImpl && ((GrReferenceElementImpl)ref).isFullyQualified()) {
      final GrDocComment doc = PsiTreeUtil.getParentOfType(ref, GrDocComment.class);
      if (doc != null) {
        if (useFqInJavadoc) return false;
      }
      else {
        if (useFqInCode) return false;
      }
    }

    final Qualifier qualifier = ref.getQualifier();

    if (qualifier instanceof GrCodeReferenceElement) {
      return true;
    }

    if (qualifier instanceof GrExpression) {
      if (qualifier instanceof GrReferenceExpression && PsiUtil.isThisReference(qualifier)) return true;
      if (qualifier instanceof GrReferenceExpression &&
          PsiImplUtil.seemsToBeQualifiedClassName((GrExpression)qualifier)) {
        final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass || resolved instanceof PsiPackage) return true;
      }
    }
    return false;
  }

  private static <Qualifier extends PsiElement> boolean cannotShortenInContext(@NotNull GrQualifiedReference<Qualifier> ref) {
    return PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) != null ||
           PsiTreeUtil.getParentOfType(ref, GroovyCodeFragment.class) != null;
  }

  private static <Qualifier extends PsiElement> boolean mayInsertImport(@NotNull GrQualifiedReference<Qualifier> ref) {
    return !(ref.getContainingFile() instanceof GroovyCodeFragment) &&
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) == null &&
           ref.getContainingFile() instanceof GroovyFileBase;
  }

  public static GrReferenceAdjuster getInstance() {
    return new GrReferenceAdjuster();
  }
}
