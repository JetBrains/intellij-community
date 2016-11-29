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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class InconsistentPropertiesEndsInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  private static final Set<Character> PROPERTY_VALUE_END_CHECK_SYMBOLS = ContainerUtil.newTroveSet('!', '?', '.', ':', ';');

  @NotNull
  @Override
  public String getName() {
    return "REPORT_INCONSISTENT_PROPERTIES_ENDS";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return InspectionsBundle.message("inconsistent.bundle.report.inconsistent.properties.ends");
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
      final Set<String> filePropertyKeys = new THashSet<>(propertiesFilesNamesMaps.get(file).keySet());
      PropertiesFile parent = parents.get(file);
      while (parent != null) {
        final Collection<String> commonKeys = ContainerUtil.intersection(propertiesFilesNamesMaps.get(parent).keySet(), filePropertyKeys);
        for (final String commonKey : commonKeys) {
          final IProperty property = file.findPropertyByKey(commonKey);
          assert property != null;
          final String propertyValue = property.getValue();
          if (StringUtil.isEmptyOrSpaces(propertyValue)) {
            continue;
          }
          final char lastChar = propertyValue.charAt(propertyValue.length() - 1);
          if (!PROPERTY_VALUE_END_CHECK_SYMBOLS.contains(lastChar)) {
            continue;
          }
          final String parentPropertyValue = propertiesFilesNamesMaps.get(parent).get(commonKey);
          if (parentPropertyValue == null) {
            continue;
          }
          final char parentLastChar = parentPropertyValue.charAt(parentPropertyValue.length() - 1);
          if (lastChar != parentLastChar) {
            final String message;
            if (PROPERTY_VALUE_END_CHECK_SYMBOLS.contains(parentLastChar)) {
              message = InspectionsBundle
                .message("inconsistent.bundle.property.inconsistent.end.parent.end.from.check.symbols", lastChar, parentLastChar,
                         parent.getName());
            } else {
              message = InspectionsBundle.message("inconsistent.bundle.property.inconsistent.end", lastChar);
            }
            final PsiElement propertyPsiElement = property.getPsiElement();
            processor.addProblemElement(refManager.getReference(file.getContainingFile()),
                                        manager.createProblemDescriptor(propertyPsiElement,
                                                                        message,
                                                                        true,
                                                                        LocalQuickFix.EMPTY_ARRAY,
                                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
          }
        }
        filePropertyKeys.removeAll(commonKeys);
        parent = parents.get(parent);
      }
    }
  }
}
