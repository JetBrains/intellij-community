// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;
import org.jetbrains.idea.devkit.util.ExtensionCandidate;
import org.jetbrains.idea.devkit.util.ExtensionLocatorKt;

import java.util.List;

final class IntentionDescriptionNotFoundInspection extends DescriptionNotFoundInspectionBase {

  @NonNls
  private static final String INTENTION_ACTION_EXTENSION_POINT = "com.intellij.intentionAction";

  IntentionDescriptionNotFoundInspection() {
    super(DescriptionType.INTENTION);
  }

  @Override
  protected boolean skipIfNotRegistered(PsiClass epClass) {
    List<ExtensionCandidate> candidates = ExtensionLocatorKt.locateExtensionsByPsiClass(epClass);

    // 1. not registered at all
    if (candidates.isEmpty()) {
      return true;
    }

    // 2. find registration under EP name
    return processIntentionActionExtension(candidates, CommonProcessors.alwaysFalse());
  }

  @Override
  protected @Nullable String getDescriptionDir(Module module, PsiClass psiClass) {
    Ref<String> customDirectory = Ref.create();
    processIntentionActionExtension(ExtensionLocatorKt.locateExtensionsByPsiClass(psiClass), extension -> {
      @SuppressWarnings("unchecked") GenericDomValue<String> descriptionDirectoryName =
        (GenericDomValue<String>)DevKitDomUtil.getTag(extension, "descriptionDirectoryName");
      if (descriptionDirectoryName != null && DomUtil.hasXml(descriptionDirectoryName)) {
        customDirectory.set(descriptionDirectoryName.getStringValue());
      }
      return false;
    });
    if (customDirectory.get() != null) {
      return customDirectory.get();
    }

    return super.getDescriptionDir(module, psiClass);
  }

  @Override
  protected boolean skipOptionalBeforeAfter(PsiClass epClass) {
    return processIntentionActionExtension(ExtensionLocatorKt.locateExtensionsByPsiClass(epClass), extension -> {
      @SuppressWarnings("unchecked") GenericDomValue<Boolean> skipBeforeAfterValue =
        (GenericDomValue<Boolean>)DevKitDomUtil.getTag(extension, "skipBeforeAfter");
      return skipBeforeAfterValue != null && DomUtil.hasXml(skipBeforeAfterValue) &&
             Boolean.TRUE.equals(skipBeforeAfterValue.getValue());
    });
  }

  private static boolean processIntentionActionExtension(List<ExtensionCandidate> candidates, Processor<Extension> processor) {
    for (ExtensionCandidate candidate : candidates) {
      Extension extension = DomUtil.findDomElement(candidate.pointer.getElement(), Extension.class, false);
      assert extension != null;
      ExtensionPoint extensionPoint = extension.getExtensionPoint();
      if (extensionPoint == null) continue;

      String epFqn = extensionPoint.getEffectiveQualifiedName();
      if (epFqn.equals(INTENTION_ACTION_EXTENSION_POINT)) {
        return processor.process(extension);
      }
    }

    return true;
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
}
