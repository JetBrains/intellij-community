// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class PropertiesPlaceholdersInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  @NotNull
  @Override
  public String getName() {
    return "REPORT_INCONSISTENT_PROPERTIES_PLACEHOLDERS";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return JavaI18nBundle.message("inconsistent.bundle.report.inconsistent.properties.placeholders");
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
      final Set<String> filePropertyKeys = new HashSet<>(propertiesFilesNamesMaps.get(file).keySet());
      PropertiesFile parent = parents.get(file);
      while (parent != null) {
        final Collection<String> commonKeys = ContainerUtil.intersection(propertiesFilesNamesMaps.get(parent).keySet(), filePropertyKeys);
        for (final String commonKey : commonKeys) {
          final IProperty property = file.findPropertyByKey(commonKey);
          assert property != null;
          final String propertyValue = property.getValue();
          if (propertyValue == null) {
            continue;
          }
          final String parentPropertyValue = propertiesFilesNamesMaps.get(parent).get(commonKey);
          if (parentPropertyValue == null) {
            continue;
          }
          final int occurrences = JavaI18nUtil.getPropertyValuePlaceholdersCount(propertyValue);
          final int parentOccurrences = JavaI18nUtil.getPropertyValuePlaceholdersCount(parentPropertyValue);
          if (occurrences != parentOccurrences) {
            final String problemDescriptorString =
              JavaI18nBundle.message("inconsistent.bundle.property.inconsistent.placeholders", parentOccurrences, parent.getName());
            final PsiElement propertyPsiElement = property.getPsiElement();
            processor.addProblemElement(refManager.getReference(file.getContainingFile()),
                                        manager.createProblemDescriptor(propertyPsiElement, problemDescriptorString, true,
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
