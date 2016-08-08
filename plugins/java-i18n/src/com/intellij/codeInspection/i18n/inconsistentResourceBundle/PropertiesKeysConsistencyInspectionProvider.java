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
public class PropertiesKeysConsistencyInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @NotNull
  @Override
  public String getName() {
    return "REPORT_INCONSISTENT_PROPERTIES";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return InspectionsBundle.message("inconsistent.bundle.report.inconsistent.properties");
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
      Set<String> parentKeys = keysUpToParent.get(parent);
      if (parent == null) {
        parentKeys = new THashSet<>();
        for (PropertiesFile otherTopLevelFile : files) {
          if (otherTopLevelFile != file && parents.get(otherTopLevelFile) == null) {
            parent = otherTopLevelFile;
            parentKeys.addAll(propertiesFilesNamesMaps.get(otherTopLevelFile).keySet());
          }
        }
        if (parent == null) continue;
      }
      Set<String> keys = new THashSet<>(propertiesFilesNamesMaps.get(file).keySet());
      keys.removeAll(parentKeys);
      for (String inconsistentKey : keys) {
        IProperty property = file.findPropertyByKey(inconsistentKey);
        assert property != null;
        String message = InspectionsBundle.message("inconsistent.bundle.property.error", inconsistentKey, parent.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(property.getPsiElement(), message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        processor.addProblemElement(refManager.getReference(file.getContainingFile()), descriptor);
      }
    }
  }
}
