// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;
import org.jetbrains.uast.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Provides gutter icon for EP code declaration to matching {@code <extensionPoint>} in {@code plugin.xml}.
 */
final class ExtensionPointDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    UElement uElement = UastUtils.getUParentForIdentifier(element);
    if (uElement instanceof UField uField) {
      if (!isExtensionPointNameDeclarationField(uField)) return;

      process(resolveEpFqn(uField), uField, element.getProject(), result);
    }
    else if (uElement instanceof UCallExpression uCallExpression) {
      if (!isExtensionPointNameDeclarationViaSuperCall(uCallExpression)) return;

      UDeclaration uDeclaration = UastUtils.getParentOfType(uCallExpression, UDeclaration.class);
      assert uDeclaration != null : uElement.asSourceString();
      process(resolveEpFqn(uCallExpression), uDeclaration, element.getProject(), result);
    }
  }

  private static void process(@Nullable @NonNls String epFqn,
                              @NotNull UDeclaration uDeclaration,
                              Project project,
                              Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (epFqn == null) return;

    final ExtensionPoint point = ExtensionPointIndex.findExtensionPoint(project,
                                                                        PluginRelatedLocatorsUtils.getCandidatesScope(project),
                                                                        epFqn);
    if (point == null) return;

    PsiElement identifier = UElementKt.getSourcePsiElement(uDeclaration.getUastAnchor());
    if (identifier == null) return;

    final ExtensionPointCandidate candidate = new ExtensionPointCandidate(SmartPointerManager.createPointer(point.getXmlTag()), epFqn);
    RelatedItemLineMarkerInfo<PsiElement> info =
      LineMarkerInfoHelper.createExtensionPointLineMarkerInfo(Collections.singletonList(candidate), identifier);
    result.add(info);
  }

  private static boolean isExtensionPointNameDeclarationViaSuperCall(@NotNull UCallExpression uCallExpression) {
    if (uCallExpression.getValueArgumentCount() != 1) return false;
    if (uCallExpression.getKind() != UastCallKind.CONSTRUCTOR_CALL) {
      if (uCallExpression.getKind() != UastCallKind.METHOD_CALL && !Objects.equals(uCallExpression.getMethodName(), "super")) {
        return false;
      }
    }

    // Kotlin EP_NAME field with CTOR call -> handled by UField branch
    if (UastUtils.getParentOfType(uCallExpression, UField.class) != null) {
      return false;
    }

    PsiMethod resolvedMethod = uCallExpression.resolve();
    if (resolvedMethod == null) return false;
    if (!resolvedMethod.isConstructor()) return false;
    return InheritanceUtil.isInheritor(resolvedMethod.getContainingClass(), KeyedExtensionCollector.class.getName());
  }


  private static @Nullable @NonNls String resolveEpFqn(@NotNull UCallExpression uCallExpression) {
    UExpression uParameter = uCallExpression.getArgumentForParameter(0);
    if (uParameter == null) return null;
    return UastUtils.evaluateString(uParameter);
  }

  private static @Nullable @NonNls String resolveEpFqn(@NotNull UField uField) {
    final UExpression initializer = uField.getUastInitializer();

    UExpression epNameExpression = null;
    if (initializer instanceof UCallExpression) {
      epNameExpression = ((UCallExpression)initializer).getArgumentForParameter(0);
    }
    else if (initializer instanceof UQualifiedReferenceExpression) {
      UExpression selector = ((UQualifiedReferenceExpression)initializer).getSelector();

      if (!(selector instanceof UCallExpression)) return null;
      epNameExpression = ((UCallExpression)selector).getArgumentForParameter(0);
    }
    if (epNameExpression == null) return null;

    return UastUtils.evaluateString(epNameExpression);
  }

  private static boolean isExtensionPointNameDeclarationField(@NotNull UField uField) {
    if (!uField.isFinal()) {
      return false;
    }

    final UExpression initializer = uField.getUastInitializer();
    if (!(initializer instanceof UCallExpression) &&
        !(initializer instanceof UQualifiedReferenceExpression)) {
      return false;
    }

    final PsiClass fieldClass = PsiTypesUtil.getPsiClass(uField.getType());
    if (fieldClass == null) {
      return false;
    }

    final String qualifiedClassName = fieldClass.getQualifiedName();
    return ExtensionPointName.class.getName().equals(qualifiedClassName) ||
           ProjectExtensionPointName.class.getName().equals(qualifiedClassName) ||
           InheritanceUtil.isInheritor(fieldClass, false, KeyedExtensionCollector.class.getName());
  }
}
