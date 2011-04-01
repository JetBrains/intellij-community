/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.i18n;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.RemovePropertyLocalFix;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.util.containers.BidirectionalMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author max
 */
public class InconsistentResourceBundleInspection extends DescriptorProviderInspection {
  private JCheckBox myReportMissingTranslationsCheckBox;
  private JCheckBox myReportInconsistentPropertiesCheckBox;
  private JPanel myOptionsPanel;
  private JCheckBox myReportDuplicatedPropertiesCheckBox;

  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_MISSING_TRANSLATIONS = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_INCONSISTENT_PROPERTIES = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_DUPLICATED_PROPERTIES = true;

  public InconsistentResourceBundleInspection() {
    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        REPORT_INCONSISTENT_PROPERTIES = myReportInconsistentPropertiesCheckBox.isSelected();
        REPORT_MISSING_TRANSLATIONS = myReportMissingTranslationsCheckBox.isSelected();
        REPORT_DUPLICATED_PROPERTIES = myReportDuplicatedPropertiesCheckBox.isSelected();
      }
    };
    myReportInconsistentPropertiesCheckBox.addActionListener(listener);
    myReportMissingTranslationsCheckBox.addActionListener(listener);
    myReportDuplicatedPropertiesCheckBox.addActionListener(listener);
  }

  @NotNull
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inconsistent.resource.bundle.display.name");
  }

  @NotNull
  public String getShortName() {
    return "InconsistentResourceBundle";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  public JComponent createOptionsPanel() {
    myReportInconsistentPropertiesCheckBox.setSelected(REPORT_INCONSISTENT_PROPERTIES);
    myReportMissingTranslationsCheckBox.setSelected(REPORT_MISSING_TRANSLATIONS);
    myReportDuplicatedPropertiesCheckBox.setSelected(REPORT_DUPLICATED_PROPERTIES);
    return myOptionsPanel;
  }


  public void runInspection(@NotNull AnalysisScope scope, @NotNull final InspectionManager manager) {
    final Set<ResourceBundle> visitedBundles = new THashSet<ResourceBundle>();
    scope.accept(new PsiRecursiveElementVisitor() {
      @Override public void visitFile(PsiFile file) {
        checkFile(file, manager, visitedBundles);
      }
    });
  }

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return JobDescriptor.EMPTY_ARRAY;
  }

  private void checkFile(@NotNull final PsiFile file, @NotNull final InspectionManager manager, final Set<ResourceBundle> visitedBundles) {
    if (!(file instanceof PropertiesFile)) return;
    final PropertiesFile propertiesFile = (PropertiesFile)file;
    ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    if (!visitedBundles.add(resourceBundle)) return;
    List<PropertiesFile> files = resourceBundle.getPropertiesFiles(manager.getProject());
    if (files.size() < 2) return;
    BidirectionalMap<PropertiesFile, PropertiesFile> parents = new BidirectionalMap<PropertiesFile, PropertiesFile>();
    for (PropertiesFile f : files) {
      PropertiesFile parent = PropertiesUtil.getParent(f, files);
      if (parent != null) {
        parents.put(f, parent);
      }
    }
    Map<PropertiesFile, Set<String>> keysUpToParent = new THashMap<PropertiesFile, Set<String>>();
    for (PropertiesFile f : files) {
      Set<String> keys = new THashSet<String>(f.getNamesMap().keySet());
      PropertiesFile parent = parents.get(f);
      while (parent != null) {
        keys.addAll(parent.getNamesMap().keySet());
        parent = parents.get(parent);
      }
      keysUpToParent.put(f, keys);
    }
    if (REPORT_MISSING_TRANSLATIONS) {
      checkMissingTranslations(parents, files, keysUpToParent, manager);
    }
    if (REPORT_INCONSISTENT_PROPERTIES) {
      checkConsistency(parents, files, keysUpToParent, manager);
    }
    if (REPORT_DUPLICATED_PROPERTIES) {
      checkDuplicatedProperties(parents, files, keysUpToParent, manager);
    }
  }

  private void checkDuplicatedProperties(final BidirectionalMap<PropertiesFile, PropertiesFile> parents, final List<PropertiesFile> files,
                                         final Map<PropertiesFile, Set<String>> keysUpToParent, final InspectionManager manager) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      if (parent == null) continue;
      Set<String> parentKeys = keysUpToParent.get(parent);
      Set<String> overriddenKeys = new THashSet<String>(file.getNamesMap().keySet());
      overriddenKeys.retainAll(parentKeys);
      for (String overriddenKey : overriddenKeys) {
        Property property = file.findPropertyByKey(overriddenKey);
        assert property != null;
        while (parent != null) {
          Property parentProperty = parent.findPropertyByKey(overriddenKey);
          if (parentProperty != null && Comparing.strEqual(property.getValue(), parentProperty.getValue())) {
            String message = InspectionsBundle.message("inconsistent.bundle.property.inherited.with.the.same.value", parent.getName());
            ProblemDescriptor descriptor = manager.createProblemDescriptor(property, message,
                                                                           RemovePropertyLocalFix.INSTANCE,
                                                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
            addProblemElement(getRefManager().getReference(file), descriptor);
          }
          parent = parents.get(parent);
        }
      }
    }
  }

  private void checkConsistency(final BidirectionalMap<PropertiesFile, PropertiesFile> parents, final List<PropertiesFile> files,
                                final Map<PropertiesFile, Set<String>> keysUpToParent, final InspectionManager manager) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      Set<String> parentKeys = keysUpToParent.get(parent);
      if (parent == null) {
        parentKeys = new THashSet<String>();
        for (PropertiesFile otherTopLevelFile : files) {
          if (otherTopLevelFile != file && parents.get(otherTopLevelFile) == null) {
            parent = otherTopLevelFile;
            parentKeys.addAll(otherTopLevelFile.getNamesMap().keySet());
          }
        }
        if (parent == null) continue;
      }
      Set<String> keys = new THashSet<String>(file.getNamesMap().keySet());
      keys.removeAll(parentKeys);
      for (String inconsistentKey : keys) {
        Property property = file.findPropertyByKey(inconsistentKey);
        assert property != null;
        String message = InspectionsBundle.message("inconsistent.bundle.property.error", inconsistentKey, parent.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(property, message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addProblemElement(getRefManager().getReference(file), descriptor);
      }
    }
  }

  private void checkMissingTranslations(final BidirectionalMap<PropertiesFile, PropertiesFile> parents, final List<PropertiesFile> files,
                                        final Map<PropertiesFile, Set<String>> keysUpToParent, final InspectionManager manager) {
    for (PropertiesFile file : files) {
      PropertiesFile parent = parents.get(file);
      if (parent == null) continue;
      List<PropertiesFile> children = parents.getKeysByValue(file);
      boolean isLeaf = children == null || children.isEmpty();
      if (!isLeaf) continue;
      Set<String> keys = file.getNamesMap().keySet();
      Set<String> parentKeys = new THashSet<String>(keysUpToParent.get(parent));
      if (parent.getLocale().getLanguage().equals(file.getLocale().getLanguage())) {
        // properties can be left untranslated in the dialect files
        keys = new THashSet<String>(keys);
        keys.addAll(parent.getNamesMap().keySet());
        parent = parents.get(parent);
        if (parent == null) continue;
        parentKeys = new THashSet<String>(keysUpToParent.get(parent));
      }
      parentKeys.removeAll(keys);
      for (String untranslatedKey : parentKeys) {
        Property untranslatedProperty = null;
        PropertiesFile untranslatedFile = parent;
        while (untranslatedFile != null) {
          untranslatedProperty = untranslatedFile.findPropertyByKey(untranslatedKey);
          if (untranslatedProperty != null) break;
          untranslatedFile = parents.get(untranslatedFile);
        }
        assert untranslatedProperty != null;
        String message = InspectionsBundle.message("inconsistent.bundle.untranslated.property.error", untranslatedKey, file.getName());
        ProblemDescriptor descriptor = manager.createProblemDescriptor(untranslatedProperty, message, false, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        addProblemElement(getRefManager().getReference(untranslatedFile), descriptor);
      }
    }
  }
}
