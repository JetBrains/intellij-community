/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ComparableImplementedButEqualsNotOverriddenInspection extends BaseInspection {
  @VisibleForTesting
  static final String ADD_NOTE_FIX_NAME = "Add 'ordering inconsistent with equals' JavaDoc note";
  @VisibleForTesting
  static final String GENERATE_EQUALS_FIX_NAME = "Generate 'equals()' method";

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("comparable.implemented.but.equals.not.overridden.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    if (infos[0] instanceof PsiAnonymousClass) {
      return new InspectionGadgetsFix[] {new GenerateEqualsMethodFix()};
    }

    return new InspectionGadgetsFix[] {
      new GenerateEqualsMethodFix(),
      new AddNoteFix()
    };
  }

  private static class GenerateEqualsMethodFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return GENERATE_EQUALS_FIX_NAME;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiClass aClass = (PsiClass)descriptor.getPsiElement().getParent();
      final StringBuilder methodText = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(aClass)) {
        methodText.append("@java.lang.Override ");
      }
      methodText.append("public ");
      methodText.append("boolean equals(Object o) {\n");
      methodText.append("if (!(o instanceof ").append(aClass.getName()).append("))").append("return false;");
      methodText.append("return compareTo((").append(aClass.getName()).append(")o)==0;\n");
      methodText.append("}");
      final PsiMethod method =
        JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText.toString(), aClass, PsiUtil.getLanguageLevel(aClass));
      final PsiElement newMethod = aClass.add(method);
      CodeStyleManager.getInstance(project).reformat(newMethod);
    }
  }

  private static class AddNoteFix extends InspectionGadgetsFix {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\*[ \t]+@");
    private static final String NOTE = " * Note: this class has a natural ordering that is inconsistent with equals.\n";

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return ADD_NOTE_FIX_NAME;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiClass aClass = (PsiClass)descriptor.getPsiElement().getParent();
      final PsiDocComment comment = aClass.getDocComment();
      if (comment == null) {
        final PsiDocComment newComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText("/**\n" + NOTE + "*/", aClass);
        aClass.addBefore(newComment, aClass.getFirstChild());
      }
      else {
        final String text = comment.getText();
        final Matcher matcher = PARAM_PATTERN.matcher(text);
        final String newCommentText = matcher.find()
                                      ? text.substring(0, matcher.start()) + NOTE + text.substring(matcher.start())
                                      : text.substring(0, text.length() - 2) + NOTE + "*/";
        final PsiDocComment newComment = JavaPsiFacade.getElementFactory(project).createDocCommentFromText(newCommentText);
        comment.replace(newComment);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CompareToAndEqualsNotPairedVisitor();
  }

  private static class CompareToAndEqualsNotPairedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isInterface()) {
        // the problem can't be fixed for an interface, so let's not report it
        return;
      }
      final PsiClass comparableClass =
        JavaPsiFacade.getInstance(aClass.getProject()).findClass(CommonClassNames.JAVA_LANG_COMPARABLE, aClass.getResolveScope());
      if (comparableClass == null || !aClass.isInheritor(comparableClass, true)) {
        return;
      }
      final PsiMethod[] comparableMethods = comparableClass.findMethodsByName(HardcodedMethodConstants.COMPARE_TO, false);
      if (comparableMethods.length == 0) { // incorrect/broken jdk
        return;
      }
      final PsiMethod comparableMethod = MethodSignatureUtil.findMethodBySuperMethod(aClass, comparableMethods[0], false);
      if (comparableMethod == null || comparableMethod.hasModifierProperty(PsiModifier.ABSTRACT) ||
        comparableMethod.getBody() == null) {
        return;
      }
      final PsiClass objectClass = ClassUtils.findObjectClass(aClass);
      if (objectClass == null) {
        return;
      }
      final PsiMethod[] equalsMethods = objectClass.findMethodsByName(HardcodedMethodConstants.EQUALS, false);
      if (equalsMethods.length != 1) { // incorrect/broken jdk
        return;
      }
      final PsiMethod equalsMethod = MethodSignatureUtil.findMethodBySuperMethod(aClass, equalsMethods[0], false);
      if (equalsMethod != null && !equalsMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final String docCommentText = StringUtil.collapseWhiteSpace(getActualCommentText(aClass.getDocComment()));
      if (StringUtil.containsIgnoreCase(docCommentText, "this class has a natural ordering that is inconsistent with equals")) {
        // see Comparable.compareTo() javadoc
        return;
      }
      registerClassError(aClass, aClass);
    }

    private static String getActualCommentText(PsiDocComment comment) {
      if (comment == null) return "";
      return Arrays.stream(comment.getChildren())
        .filter(e -> (e instanceof PsiDocToken) && ((PsiDocToken)e).getTokenType() == JavaDocTokenType.DOC_COMMENT_DATA)
        .map(PsiElement::getText)
        .collect(Collectors.joining());
    }
  }
}
