/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import com.siyeh.ig.ui.UiUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class IgnoreResultOfCallInspection extends BaseInspection {

  public static final String SHORT_NAME_RETURN_VALUE_ANNOTATION = "CheckReturnValue";
  private static final Set<String> RETURN_VALUE_ANNOTATIONS = ContainerUtil
    .immutableSet("javax.annotation.CheckReturnValue", "org.assertj.core.util.CheckReturnValue ");
  private static final CallMatcher STREAM_COLLECT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_STREAM_STREAM, "collect").parameterCount(1);
  private static final CallMatcher COLLECTOR_TO_COLLECTION =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS, "toCollection").parameterCount(1);
  private static final CallMapper<String> KNOWN_EXCEPTIONAL_SIDE_EFFECTS = new CallMapper<String>()
    .register(CallMatcher.staticCall("java.util.regex.Pattern", "compile"), "java.util.regex.PatternSyntaxException")
    .register(CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_INTEGER, "parseInt", "valueOf"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_LONG, "parseLong", "valueOf"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_DOUBLE, "parseDouble", "valueOf"),
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat", "valueOf")), "java.lang.NumberFormatException")
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS,
                                       "getMethod", "getDeclaredMethod", "getConstructor", "getDeclaredConstructor"),
              "java.lang.NoSuchMethodException")
    .register(CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_CLASS,
                                       "getField", "getDeclaredField"), "java.lang.NoSuchFieldException");
  private static final CallMatcher MOCK_LIBS_EXCLUDED_QUALIFIER_CALLS =
    CallMatcher.anyOf(
      CallMatcher.instanceCall("org.mockito.stubbing.Stubber", "when"),
      CallMatcher.staticCall("org.mockito.Mockito", "verify"),
      CallMatcher.instanceCall("org.jmock.Expectations", "allowing", "ignoring", "never", "one", "oneOf", "with")
        .parameterTypes("T"));

  public static final String SHORT_NAME_IGNORE_ANNOTATIONS = "CanIgnoreReturnValue";
  private static final Set<String> IGNORE_ANNOTATIONS = ContainerUtil
    .immutableSet("org.assertj.core.util.CanIgnoreReturnValue", "com.google.errorprone.annotations.CanIgnoreReturnValue");
  protected final MethodMatcher myMethodMatcher;
  /**
   * @noinspection PublicField
   */
  public boolean m_reportAllNonLibraryCalls = false;
  public boolean m_reportClassAndPackageShortNameAnnotated = false;

  public IgnoreResultOfCallInspection() {
    myMethodMatcher = new MethodMatcher(true, "callCheckString")
      .add("java.io.File", ".*")
      .add("java.io.InputStream", "read|skip|available|markSupported")
      .add("java.io.Reader", "read|skip|ready|markSupported")
      .add("java.lang.Boolean", ".*")
      .add("java.lang.Byte", ".*")
      .add("java.lang.Character", ".*")
      .add("java.lang.Double", ".*")
      .add("java.lang.Float", ".*")
      .add("java.lang.Integer", ".*")
      .add("java.lang.Long", ".*")
      .add("java.lang.Math", ".*")
      .add("java.lang.Object", "equals|hashCode|toString")
      .add("java.lang.Short", ".*")
      .add("java.lang.StrictMath", ".*")
      .add("java.lang.String", ".*")
      .add("java.lang.Thread", "interrupted")
      .add("java.math.BigInteger", ".*")
      .add("java.math.BigDecimal", ".*")
      .add("java.net.InetAddress", ".*")
      .add("java.net.URI", ".*")
      .add("java.util.Arrays", ".*")
      .add("java.util.List", "of")
      .add("java.util.Set", "of")
      .add("java.util.Map", "of|ofEntries|entry")
      .add("java.util.Collections", "(?!addAll).*")
      .add("java.util.UUID",".*")
      .add("java.util.regex.Matcher","pattern|toMatchResult|start|end|group|groupCount|matches|find|lookingAt|quoteReplacement|replaceAll|replaceFirst|regionStart|regionEnd|hasTransparentBounds|hasAnchoringBounds|hitEnd|requireEnd")
      .add("java.util.regex.Pattern",".*")
      .add("java.util.stream.BaseStream",".*")
      .finishDefault();
  }

  @Override
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ListTable table = new ListTable(new ListWrappingTableModel(
      Arrays.asList(myMethodMatcher.getClassNames(), myMethodMatcher.getMethodNamePatterns()),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
      InspectionGadgetsBundle.message("result.of.method.call.ignored.method.column.title")));
    final JPanel tablePanel = UiUtils.createAddRemoveTreeClassChooserPanel(table, "Choose class");
    panel.add(tablePanel, BorderLayout.CENTER);

    MultipleCheckboxOptionsPanel checkBox = new MultipleCheckboxOptionsPanel(this);

    checkBox.addCheckbox(InspectionGadgetsBundle.message("result.of.method.call.ignored.non.library.option"), "m_reportAllNonLibraryCalls");

    checkBox.addCheckbox(InspectionGadgetsBundle.message("result.of.method.call.ignored.class.and.package.short.name"),
                         "m_reportClassAndPackageShortNameAnnotated");

    panel.add(checkBox, BorderLayout.SOUTH);
    return panel;
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ResultOfMethodCallIgnored";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    final String className = containingClass.getName();
    return InspectionGadgetsBundle.message("result.of.method.call.ignored.problem.descriptor", className);
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    myMethodMatcher.readSettings(element);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    super.writeSettings(element);
    myMethodMatcher.writeSettings(element);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IgnoreResultOfCallVisitor();
  }

  private class IgnoreResultOfCallVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(expression))) {
        PsiElement resolve = expression.resolve();
        if (resolve instanceof PsiMethod) {
          visitCalledExpression(expression, (PsiMethod)resolve, null);
        }
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (ExpressionUtils.isVoidContext(expression)) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null || method.isConstructor()) {
          return;
        }
        visitCalledExpression(expression, method, expression.getParent());
      }
    }

    private void visitCalledExpression(PsiExpression call,
                                       PsiMethod method,
                                       @Nullable PsiElement errorContainer) {
      if (shouldReport(call, method, errorContainer)) {
        registerMethodCallOrRefError(call, method.getContainingClass());
      }
    }

    private boolean shouldReport(PsiExpression call, PsiMethod method, @Nullable PsiElement errorContainer) {
      final PsiType returnType = method.getReturnType();
      if (PsiType.VOID.equals(returnType) || TypeUtils.typeEquals(CommonClassNames.JAVA_LANG_VOID, returnType)) return false;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) return false;
      if (errorContainer != null && PsiUtilCore.hasErrorElementChild(errorContainer)) return false;
      if (PropertyUtil.isSimpleGetter(method)) {
        return !isIgnored(method, null);
      }
      if (method instanceof PsiCompiledElement) {
        PsiMethod sourceMethod = ObjectUtils.tryCast(method.getNavigationElement(), PsiMethod.class);
        if (sourceMethod != null && PropertyUtil.isSimpleGetter(sourceMethod)) {
          return !isIgnored(method, null);
        }
      }
      if (m_reportAllNonLibraryCalls && !LibraryUtil.classIsInLibrary(aClass)) {
        return !isIgnored(method, null);
      }

      if (isKnownExceptionalSideEffectCaught(call)) return false;

      if (isPureMethod(method, call)) {
        return !isIgnored(method, null);
      }

      PsiAnnotation annotation = findAnnotationInTree(method, null, RETURN_VALUE_ANNOTATIONS, SHORT_NAME_RETURN_VALUE_ANNOTATION);

      if (!myMethodMatcher.matches(method) && annotation == null) return false;
      if (isHardcodedException(call)) return false;
      return !isIgnored(method, annotation);
    }

    private boolean isIgnored(@NotNull PsiMethod method, @Nullable PsiAnnotation annotation) {
      final PsiElement owner = annotation == null ? null : (PsiElement)annotation.getOwner();
      return findAnnotationInTree(method, owner, IGNORE_ANNOTATIONS, SHORT_NAME_IGNORE_ANNOTATIONS) != null;
    }


    private PsiAnnotation findAnnotationByShortNameCheckReturnValue(PsiModifierListOwner listOwner, String shortName) {

      if (StringUtil.isEmpty(shortName)) {
        return null;
      }

      final String findName;

      if (shortName.startsWith(".")) {
        findName = shortName;
      }
      else {
        findName = "." + shortName;
      }

      final PsiModifierList list = listOwner.getModifierList();
      if (list == null) {
        return null;
      }
      for (PsiAnnotation annotation : list.getAnnotations()) {
        String name = annotation.getQualifiedName();
        if (name != null && (name.endsWith(findName) || name.equals(shortName))) {
          return annotation;
        }
      }
      return null;
    }

    private boolean isKnownExceptionalSideEffectCaught(PsiExpression call) {
      String exception = null;
      if (call instanceof PsiMethodCallExpression) {
        exception = KNOWN_EXCEPTIONAL_SIDE_EFFECTS.mapFirst((PsiMethodCallExpression)call);
      }
      else if (call instanceof PsiMethodReferenceExpression) {
        exception = KNOWN_EXCEPTIONAL_SIDE_EFFECTS.mapFirst((PsiMethodReferenceExpression)call);
      }
      if (exception == null) return false;
      PsiClass exceptionClass = JavaPsiFacade.getInstance(call.getProject()).findClass(exception, call.getResolveScope());
      if (exceptionClass == null) return false;
      PsiTryStatement parentTry = PsiTreeUtil.getParentOfType(call, PsiTryStatement.class);
      if (parentTry == null || !PsiTreeUtil.isAncestor(parentTry.getTryBlock(), call, true)) return false;
      return ExceptionUtils.getExceptionTypesHandled(parentTry).stream()
        .anyMatch(type -> InheritanceUtil.isInheritor(exceptionClass, type.getCanonicalText()));
    }

    private boolean isHardcodedException(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) return false;
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (STREAM_COLLECT.test(call)) {
        PsiMethodCallExpression collector =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(call.getArgumentList().getExpressions()[0]), PsiMethodCallExpression.class);
        if (COLLECTOR_TO_COLLECTION.test(collector)) {
          PsiLambdaExpression lambda = ObjectUtils
            .tryCast(PsiUtil.skipParenthesizedExprDown(collector.getArgumentList().getExpressions()[0]), PsiLambdaExpression.class);
          if (lambda != null) {
            PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
            if (body instanceof PsiReferenceExpression && ((PsiReferenceExpression)body).resolve() instanceof PsiVariable) {
              // .collect(toCollection(() -> var)) : the result is written into given collection
              return true;
            }
          }
        }
      }

      return false;
    }

    private boolean isPureMethod(PsiMethod method, PsiExpression call) {
      final boolean honorInferred = Registry.is("ide.ignore.call.result.inspection.honor.inferred.pure");
      if (!honorInferred && !JavaMethodContractUtil.hasExplicitContractAnnotation(method)) return false;
      if (!JavaMethodContractUtil.isPure(method) || hasTrivialReturnValue(method)) return false;
      if (call instanceof PsiMethodCallExpression) {
        PsiMethodCallExpression previousCall = MethodCallUtils.getQualifierMethodCall((PsiMethodCallExpression)call);
        if (MOCK_LIBS_EXCLUDED_QUALIFIER_CALLS.test(previousCall)) return false;
      }
      if (!SideEffectChecker.mayHaveExceptionalSideEffect(method)) return true;
      if (!(call instanceof PsiCallExpression) || JavaMethodContractUtil.getMethodCallContracts(method, null).isEmpty()) return false;
      CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(call);
      return result != null && result.cannotFailByContract((PsiCallExpression)call);
    }

    private boolean hasTrivialReturnValue(PsiMethod method) {
      List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
      ContractReturnValue nonFailingReturnValue = JavaMethodContractUtil.getNonFailingReturnValue(contracts);
      return nonFailingReturnValue != null &&
             (nonFailingReturnValue.equals(ContractReturnValue.returnThis()) ||
              nonFailingReturnValue instanceof ContractReturnValue.ParameterReturnValue);
    }

    private void registerMethodCallOrRefError(PsiExpression call, PsiClass aClass) {
      if (call instanceof PsiMethodCallExpression) {
        registerMethodCallError((PsiMethodCallExpression)call, aClass);
      }
      else if (call instanceof PsiMethodReferenceExpression) {
        registerError(ObjectUtils.notNull(((PsiMethodReferenceExpression)call).getReferenceNameElement(), call), aClass);
      }
    }

    @Nullable
    private PsiAnnotation findAnnotationInTree(PsiElement element, @Nullable PsiElement stop, @NotNull Set<String> fqAnnotationNames, @NotNull String shortAnnotationName) {
      while (element != null) {
        if (element == stop) {
          return null;
        }
        if (element instanceof PsiModifierListOwner) {
          final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
          final PsiAnnotation annotation;
          if (m_reportClassAndPackageShortNameAnnotated) {
            annotation =
              findAnnotationByShortNameCheckReturnValue(modifierListOwner, shortAnnotationName);
          }
          else {
            annotation =
              AnnotationUtil.findAnnotationInHierarchy(modifierListOwner, fqAnnotationNames);
          }
          if (annotation != null) {
            return annotation;
          }
        }

        if (element instanceof PsiClassOwner) {
          final PsiClassOwner classOwner = (PsiClassOwner)element;
          final String packageName = classOwner.getPackageName();
          final PsiPackage aPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
          if (aPackage == null) {
            return null;
          }
          final PsiAnnotation annotation;
          if (m_reportClassAndPackageShortNameAnnotated) {
            annotation =
              findAnnotationByShortNameCheckReturnValue(aPackage, shortAnnotationName);
          }
          else {
            annotation =
              AnnotationUtil.findAnnotation(aPackage, fqAnnotationNames);
          }

          if (annotation != null) {
            // Check that annotation actually belongs to the same library/source root
            // which could be important in case of split-packages
            VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
            VirtualFile currentFile = classOwner.getVirtualFile();
            if (annotationFile != null && currentFile != null) {
              ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(element.getProject());
              VirtualFile annotationClassRoot = projectFileIndex.getClassRootForFile(annotationFile);
              VirtualFile currentClassRoot = projectFileIndex.getClassRootForFile(currentFile);
              if (!Objects.equals(annotationClassRoot, currentClassRoot)) {
                return null;
              }
            }
          }
          return annotation;
        }

        element = element.getContext();
      }
      return null;
    }
  }
}