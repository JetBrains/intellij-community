// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public final class InconsistentPropertiesEndsInspectionProvider implements InconsistentResourceBundleInspectionProvider {
  private static final Set<Character> PROPERTY_VALUE_END_CHECK_SYMBOLS = Set.of('!', '?', '.', ':', ';');
  private static final char NULL = '\0';

  @NotNull
  @Override
  public String getName() {
    return "REPORT_INCONSISTENT_PROPERTIES_ENDS";
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return JavaI18nBundle.message("inconsistent.bundle.report.inconsistent.properties.ends");
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
          final char parentLastChar = parentPropertyValue.isEmpty() ? NULL : parentPropertyValue.charAt(parentPropertyValue.length() - 1);
          if (lastChar != parentLastChar) {
            final String message;
            if (PROPERTY_VALUE_END_CHECK_SYMBOLS.contains(parentLastChar)) {
              message = JavaI18nBundle
                .message("inconsistent.bundle.property.inconsistent.end.parent.end.from.check.symbols", lastChar, parentLastChar,
                         parent.getName());
            } else {
              message = JavaI18nBundle.message("inconsistent.bundle.property.inconsistent.end", lastChar);
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
