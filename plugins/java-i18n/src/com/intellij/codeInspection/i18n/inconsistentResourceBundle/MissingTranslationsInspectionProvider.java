/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.util.containers.BidirectionalMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class MissingTranslationsInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @NotNull
  @Override
  public String getName() {
    return "REPORT_MISSING_TRANSLATIONS";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return InspectionsBundle.message("inconsistent.bundle.report.missing.translations");
  }

  @Override
  public void check(BidirectionalMap<PropertiesFile, PropertiesFile> parents,
                    List<PropertiesFile> files,
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
      Set<String> parentKeys = new THashSet<>(keysUpToParent.get(parent));
      if (parent.getLocale().getLanguage().equals(file.getLocale().getLanguage())) {
        // properties can be left untranslated in the dialect files
        keys = new THashSet<>(keys);
        keys.addAll(propertiesFilesNamesMaps.get(parent).keySet());
        parent = parents.get(parent);
        if (parent == null) continue;
        parentKeys = new THashSet<>(keysUpToParent.get(parent));
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
        String message = InspectionsBundle.message("inconsistent.bundle.untranslated.property.error", untranslatedKey, file.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(untranslatedProperty.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(untranslatedFile.getContainingFile()), descriptor);
      }
    }
  }
}
