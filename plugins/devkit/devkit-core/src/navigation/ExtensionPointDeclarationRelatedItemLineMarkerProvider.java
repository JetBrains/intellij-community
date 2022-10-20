// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;
import org.jetbrains.uast.*;

import java.util.Collection;
import java.util.Collections;

/**
 * Navigate from EP field to declaration in {@code plugin.xml}.
 */
public class ExtensionPointDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    UElement uElement = UastUtils.getUParentForIdentifier(element);
    if (!(uElement instanceof UField)) {
      return;
    }

    process((UField)uElement, element.getProject(), result);
  }

  private static void process(UField uField, Project project, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (!isExtensionPointNameDeclarationField(uField)) return;

    final String epFqn = resolveEpFqn(uField);
    if (epFqn == null) return;

    final ExtensionPoint point = ExtensionPointIndex.findExtensionPoint(project,
                                                                        PluginRelatedLocatorsUtils.getCandidatesScope(project),
                                                                        epFqn);
    if (point == null) return;

    PsiElement identifier = UElementKt.getSourcePsiElement(uField.getUastAnchor());
    if (identifier == null) return;

    final ExtensionPointCandidate candidate = new ExtensionPointCandidate(SmartPointerManager.createPointer(point.getXmlTag()), epFqn);
    RelatedItemLineMarkerInfo<PsiElement> info =
      LineMarkerInfoHelper.createExtensionPointLineMarkerInfo(Collections.singletonList(candidate), identifier);
    result.add(info);
  }

  @Nullable
  private static String resolveEpFqn(UField uField) {
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

  private static boolean isExtensionPointNameDeclarationField(UField uField) {
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
