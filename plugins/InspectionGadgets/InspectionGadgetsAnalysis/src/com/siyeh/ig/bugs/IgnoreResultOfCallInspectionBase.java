/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodMatcher;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class IgnoreResultOfCallInspectionBase extends BaseInspection {
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
      CallMatcher.staticCall(CommonClassNames.JAVA_LANG_FLOAT, "parseFloat", "valueOf")), "java.lang.NumberFormatException");

  /**
   * @noinspection PublicField
   */
  public boolean m_reportAllNonLibraryCalls = false;

  protected final MethodMatcher myMethodMatcher;

  public IgnoreResultOfCallInspectionBase() {
    myMethodMatcher = new MethodMatcher(true, "callCheckString")
      .add("java.io.File", ".*")
      .add("java.io.InputStream","read|skip|available|markSupported")
      .add("java.io.Reader","read|skip|ready|markSupported")
      .add("java.lang.Boolean",".*")
      .add("java.lang.Byte",".*")
      .add("java.lang.Character",".*")
      .add("java.lang.Double",".*")
      .add("java.lang.Float",".*")
      .add("java.lang.Integer",".*")
      .add("java.lang.Long",".*")
      .add("java.lang.Math",".*")
      .add("java.lang.Object","equals|hashCode|toString")
      .add("java.lang.Short",".*")
      .add("java.lang.StrictMath",".*")
      .add("java.lang.String",".*")
      .add("java.math.BigInteger",".*")
      .add("java.math.BigDecimal",".*")
      .add("java.net.InetAddress",".*")
      .add("java.net.URI",".*")
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

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ResultOfMethodCallIgnored";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("result.of.method.call.ignored.display.name");
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
      PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpressionStatement ||
          parent instanceof PsiLambdaExpression && PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent))) {
        final PsiMethod method = expression.resolveMethod();
        if (method == null || method.isConstructor()) {
          return;
        }
        visitCalledExpression(expression, method, parent);
      }
    }

    private void visitCalledExpression(PsiExpression call,
                                       PsiMethod method,
                                       @Nullable PsiElement errorContainer) {
      final PsiType returnType = method.getReturnType();
      if (PsiType.VOID.equals(returnType)) return;
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) return;
      if (errorContainer != null && PsiUtilCore.hasErrorElementChild(errorContainer)) return;
      if (PropertyUtil.isSimpleGetter(method)) {
        registerMethodCallOrRefError(call, aClass);
        return;
      }
      if (m_reportAllNonLibraryCalls && !LibraryUtil.classIsInLibrary(aClass)) {
        registerMethodCallOrRefError(call, aClass);
        return;
      }

      if (isKnownExceptionalSideEffectCaught(call)) return;

      if (isPureMethod(method)) {
        registerMethodCallOrRefError(call, aClass);
        return;
      }

      PsiAnnotation annotation = findAnnotationInTree(method, null, Collections.singleton("javax.annotation.CheckReturnValue"));
      if (annotation == null) {
        annotation = getAnnotationByShortNameCheckReturnValue(method);
      }

      if (annotation != null) {
        final PsiElement owner = (PsiElement)annotation.getOwner();
        if (findAnnotationInTree(method, owner, Collections.singleton("com.google.errorprone.annotations.CanIgnoreReturnValue")) != null) {
          return;
        }
      }
      if (!myMethodMatcher.matches(method) && annotation == null) return;
      if (isHardcodedException(call)) return;

      registerMethodCallOrRefError(call, aClass);
    }

    private PsiAnnotation getAnnotationByShortNameCheckReturnValue(PsiMethod method) {
      for (PsiAnnotation psiAnnotation : method.getAnnotations()) {
        String qualifiedName = psiAnnotation.getQualifiedName();
        if (qualifiedName != null && "CheckReturnValue".equals(StringUtil.getShortName(qualifiedName))) {
          return psiAnnotation;
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

    private boolean isPureMethod(PsiMethod method) {
      final PsiAnnotation anno = ControlFlowAnalyzer.findContractAnnotation(method);
      if (anno == null) return false;
      final boolean honorInferred = Registry.is("ide.ignore.call.result.inspection.honor.inferred.pure");
      if (!honorInferred && AnnotationUtil.isInferredAnnotation(anno)) return false;
      return Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(anno, "pure")) &&
             !SideEffectChecker.mayHaveExceptionalSideEffect(method);
    }

    private void registerMethodCallOrRefError(PsiExpression call, PsiClass aClass) {
      if (call instanceof PsiMethodCallExpression) {
        registerMethodCallError((PsiMethodCallExpression)call, aClass);
      }
      else if (call instanceof PsiMethodReferenceExpression){
        registerError(ObjectUtils.notNull(((PsiMethodReferenceExpression)call).getReferenceNameElement(), call), aClass);
      }
    }

    @Nullable
    private PsiAnnotation findAnnotationInTree(PsiElement element, @Nullable PsiElement stop, @NotNull Set<String> fqAnnotationNames) {
      while (element != null) {
        if (element == stop) {
          return null;
        }
        if (element instanceof PsiModifierListOwner) {
          final PsiModifierListOwner modifierListOwner = (PsiModifierListOwner)element;
          final PsiAnnotation annotation =
            AnnotationUtil.findAnnotationInHierarchy(modifierListOwner, fqAnnotationNames);
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
          PsiAnnotation annotation = AnnotationUtil.findAnnotation(aPackage, fqAnnotationNames);
          if(annotation != null) {
            // Check that annotation actually belongs to the same library/source root
            // which could be important in case of split-packages
            VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
            VirtualFile currentFile = classOwner.getVirtualFile();
            if(annotationFile != null && currentFile != null) {
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
