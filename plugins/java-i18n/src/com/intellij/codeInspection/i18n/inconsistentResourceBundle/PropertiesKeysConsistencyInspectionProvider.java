// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.containers.BidirectionalMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class PropertiesKeysConsistencyInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @NotNull
  @Override
  public String getName() {
    return "REPORT_INCONSISTENT_PROPERTIES";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return JavaI18nBundle.message("inconsistent.bundle.report.inconsistent.properties");
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
      Set<String> parentKeys = keysUpToParent.get(parent);
      if (parent == null) {
        parentKeys = new HashSet<>();
        for (PropertiesFile otherTopLevelFile : files) {
          if (otherTopLevelFile != file && parents.get(otherTopLevelFile) == null) {
            parent = otherTopLevelFile;
            parentKeys.addAll(propertiesFilesNamesMaps.get(otherTopLevelFile).keySet());
          }
        }
        if (parent == null) continue;
      }
      Set<String> keys = new HashSet<>(propertiesFilesNamesMaps.get(file).keySet());
      keys.removeAll(parentKeys);
      for (String inconsistentKey : keys) {
        IProperty property = file.findPropertyByKey(inconsistentKey);
        if (property == null) continue;
        String message = JavaI18nBundle.message("inconsistent.bundle.property.error", inconsistentKey, parent.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(property.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
      }
    }
  }
}
