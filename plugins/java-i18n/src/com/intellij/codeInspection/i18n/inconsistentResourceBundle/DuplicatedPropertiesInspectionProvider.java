// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesQuickFixFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class DuplicatedPropertiesInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @Override
  public @NotNull String getName() {
    return "REPORT_DUPLICATED_PROPERTIES";
  }

  @Override
  public @NotNull String getPresentableName() {
    return JavaI18nBundle.message("inconsistent.bundle.report.duplicate.properties.values");
  }

  @Override
  public void check(BidirectionalMap<PropertiesFile, PropertiesFile> parents,
                    List<? extends PropertiesFile> files,
                    Map<PropertiesFile, Set<String>> keysUpToParent,
                    Map<PropertiesFile, Map<String, String>> propertiesFilesNamesMaps,
                    InspectionManager manager,
                    RefManager refManager,
                    ProblemDescriptionsProcessor processor) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      if (parent == null) continue;
      Set<String> parentKeys = keysUpToParent.get(parent);
      Set<String> overriddenKeys = new HashSet<>(propertiesFilesNamesMaps.get(file).keySet());
      overriddenKeys.retainAll(parentKeys);
      for (String overriddenKey : overriddenKeys) {
        IProperty property = file.findPropertyByKey(overriddenKey);
        assert property != null;
        while (parent != null) {
          IProperty parentProperty = parent.findPropertyByKey(overriddenKey);
          if (parentProperty != null && Comparing.strEqual(property.getValue(), parentProperty.getValue())) {
            String message = JavaI18nBundle.message("inconsistent.bundle.property.inherited.with.the.same.value", parent.getName());
            PsiElement propertyElement = property.getPsiElement();
            LocalQuickFix fix =
              propertyElement instanceof Property prop ? PropertiesQuickFixFactory.getInstance().createRemovePropertyLocalFix(prop) : null;
            ProblemDescriptor descriptor = manager.createProblemDescriptor(propertyElement, message,
                                                                           fix,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
          }
          parent = parents.get(parent);
        }
      }
    }
  }
}
