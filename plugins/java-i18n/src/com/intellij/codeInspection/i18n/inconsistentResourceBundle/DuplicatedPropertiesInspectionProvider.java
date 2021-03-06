// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.RemovePropertyLocalFix;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.Comparing;
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
  @NotNull
  @Override
  public String getName() {
    return "REPORT_DUPLICATED_PROPERTIES";
  }

  @NotNull
  @Override
  public String getPresentableName() {
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
            ProblemDescriptor descriptor = manager.createProblemDescriptor(property.getPsiElement(), message,
                                                                           RemovePropertyLocalFix.INSTANCE,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
          }
          parent = parents.get(parent);
        }
      }
    }
  }
}
