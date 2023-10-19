// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

final class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  protected boolean skipIfNotRegistered(PsiClass epClass) {
    return findCandidate(epClass) == null;
  }

  @Override
  protected boolean skipOptionalBeforeAfter(PsiClass epClass) {
    ExtensionCandidate candidate = findCandidate(epClass);
    assert candidate != null;
    DomElement domElement = DomManager.getDomManager(epClass.getProject()).getDomElement(candidate.pointer.getElement());
    if (!(domElement instanceof Extension)) return false;

    @SuppressWarnings("unchecked") GenericDomValue<Boolean> skipBeforeAfterValue =
      (GenericDomValue<Boolean>)DevKitDomUtil.getTag(domElement, "skipBeforeAfter");
    return skipBeforeAfterValue != null && Boolean.TRUE.equals(skipBeforeAfterValue.getValue());
  }

  @Override
  @NotNull
  protected String getHasNotDescriptionError(Module module, PsiClass psiClass) {
    return DevKitBundle.message("inspections.intention.description.not.found");
  }

  @Override
  @NotNull
  protected String getHasNotBeforeAfterError() {
    return DevKitBundle.message("inspections.intention.description.no.before.after.template");
  }

  @Nullable
  private static ExtensionCandidate findCandidate(PsiClass epClass) {
    return ContainerUtil.getOnlyItem(ExtensionLocatorKt.locateExtensionsByPsiClass(epClass));
  }
}
