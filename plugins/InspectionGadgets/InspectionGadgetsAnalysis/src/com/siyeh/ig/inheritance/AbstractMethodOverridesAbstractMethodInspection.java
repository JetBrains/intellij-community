/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.ThrowableRunnable;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class AbstractMethodOverridesAbstractMethodInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreJavaDoc = false;

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AbstractMethodOverridesAbstractMethodFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreJavaDoc", InspectionGadgetsBundle.message(
        "abstract.method.overrides.abstract.method.ignore.different.javadoc.option")));
  }

  private static class AbstractMethodOverridesAbstractMethodFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      doFix(project, descriptor, false);
    }

    private static void doFix(Project project, ProblemDescriptor descriptor, boolean inPreview) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodNameIdentifier.getParent();
      assert method != null;
      final PsiMethod[] superMethods = method.findSuperMethods();
      SearchScope scope = GlobalSearchScope.allScope(project);
      if (inPreview) {
        scope = scope.intersectWith(new LocalSearchScope(method.getContainingFile()));
      }
      final Collection<PsiReference> references = ReferencesSearch.search(method, scope).findAll();
      final List<PsiElement> elements =
        references.stream().map(ref -> ref.getElement())
          .filter(a -> a instanceof PsiDocMethodOrFieldRef)
          .collect(Collectors.toCollection(() -> new SmartList<>()));
      elements.add(method);
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) {
        return;
      }
      ThrowableRunnable<RuntimeException> fixRefsRunnable = () -> {
        deleteElement(method);
        references.forEach(a -> a.bindToElement(superMethods[0]));
      };
      if (inPreview) {
        fixRefsRunnable.run();
      } else {
        WriteAction.run(fixRefsRunnable);
      }
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      doFix(project, previewDescriptor, true);
      return IntentionPreviewInfo.DIFF;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractMethodOverridesAbstractMethodVisitor();
  }

  private class AbstractMethodOverridesAbstractMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor() || !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      boolean overrideDefault = false;
      boolean accept = false;
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        overrideDefault |= superMethod.hasModifierProperty(PsiModifier.DEFAULT);
        if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          continue;
        }
        if (overrideDefault) {
          return;
        }
        accept |= methodsHaveSameReturnTypes(method, superMethod) &&
                  haveSameExceptionSignatures(method, superMethod) &&
                  method.isVarArgs() == superMethod.isVarArgs();

        if (ignoreJavaDoc && !haveSameJavaDoc(method, superMethod)) {
          return;
        }
        if (!methodsHaveSameAnnotationsAndModifiers(method, superMethod)) {
          return;
        }
      }
      if (accept && !overrideDefault) {
        registerMethodError(method);
      }
    }
  }

  public static boolean methodsHaveSameAnnotationsAndModifiers(PsiMethod method, PsiMethod superMethod) {
    if (!MethodUtils.haveEquivalentModifierLists(method, superMethod)) {
      return false;
    }
    final PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != superParameters.length) {
      return false;
    }
    for (int i = 0, length = superParameters.length; i < length; i++) {
      if (!haveSameAnnotations(parameters[i], superParameters[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean haveSameAnnotations(PsiModifierListOwner owner1, PsiModifierListOwner owner2) {
    final PsiModifierList modifierList = owner1.getModifierList();
    final PsiModifierList superModifierList = owner2.getModifierList();
    return (modifierList == null || superModifierList == null)
           ? modifierList == superModifierList
           : AnnotationUtil.equal(modifierList.getAnnotations(), superModifierList.getAnnotations());
  }

  static boolean haveSameJavaDoc(PsiMethod method, PsiMethod superMethod) {
    final PsiDocComment superDocComment = superMethod.getDocComment();
    final PsiDocComment docComment = method.getDocComment();
    if (superDocComment == null) {
      if (docComment != null) {
        return false;
      }
    } else if (docComment != null) {
      if (!superDocComment.getText().equals(docComment.getText())) {
        return false;
      }
    }
    return true;
  }

  public static boolean haveSameExceptionSignatures(PsiMethod method1, PsiMethod method2) {
    final PsiReferenceList list1 = method1.getThrowsList();
    final PsiClassType[] exceptions1 = list1.getReferencedTypes();
    final PsiReferenceList list2 = method2.getThrowsList();
    final PsiClassType[] exceptions2 = list2.getReferencedTypes();
    if (exceptions1.length != exceptions2.length) {
      return false;
    }
    final Set<PsiClassType> set1 = new HashSet<>(Arrays.asList(exceptions1));
    for (PsiClassType anException : exceptions2) {
      if (!set1.contains(anException)) {
        return false;
      }
    }
    return true;
  }

  public static boolean methodsHaveSameReturnTypes(PsiMethod method1, PsiMethod method2) {
    final PsiType type1 = method1.getReturnType();
    if (type1 == null) {
      return false;
    }
    final PsiType type2 = method2.getReturnType();
    if (type1 instanceof PsiClassType && type2 instanceof PsiClassType) {
      final PsiClass superClass = method2.getContainingClass();
      final PsiClass aClass = method1.getContainingClass();
      if (aClass == null || superClass == null) return false;
      final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      return type1.equals(substitutor.substitute(type2)) && !(((PsiClassType)type1).resolve() instanceof PsiTypeParameter);
    }
    else {
      return type1.equals(type2);
    }
  }
}