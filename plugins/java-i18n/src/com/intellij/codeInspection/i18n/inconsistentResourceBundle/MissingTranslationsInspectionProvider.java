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
public final class MissingTranslationsInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @NotNull
  @Override
  public String getName() {
    return "REPORT_MISSING_TRANSLATIONS";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return JavaI18nBundle.message("inconsistent.bundle.report.missing.translations");
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
      List<PropertiesFile> children = parents.getKeysByValue(file);
      boolean isLeaf = children == null || children.isEmpty();
      if (!isLeaf) continue;
      Set<String> keys = propertiesFilesNamesMaps.get(file).keySet();
      Set<String> parentKeys = new HashSet<>(keysUpToParent.get(parent));
      if (parent.getLocale().getLanguage().equals(file.getLocale().getLanguage())) {
        // properties can be left untranslated in the dialect files
        keys = new HashSet<>(keys);
        keys.addAll(propertiesFilesNamesMaps.get(parent).keySet());
        parent = parents.get(parent);
        if (parent == null) continue;
        parentKeys = new HashSet<>(keysUpToParent.get(parent));
      }
      parentKeys.removeAll(keys);
      for (String untranslatedKey : parentKeys) {
        IProperty untranslatedProperty = null;
        PropertiesFile untranslatedFile = parent;
        while (untranslatedFile != null) {
          untranslatedProperty = untranslatedFile.findPropertyByKey(untranslatedKey);
          if (untranslatedProperty != null) break;
          untranslatedFile = parents.get(untranslatedFile);
        }
        assert untranslatedProperty != null;
        String message = JavaI18nBundle.message("inconsistent.bundle.untranslated.property.error", untranslatedKey, file.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(untranslatedProperty.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(untranslatedFile.getContainingFile()), descriptor);
      }
    }
  }
}
