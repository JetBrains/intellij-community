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
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.ui.OptionAccessor;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

/**
 * @author max
 */
public class InconsistentResourceBundleInspection extends GlobalSimpleInspectionTool {
  private static final Key<Set<ResourceBundle>> VISITED_BUNDLES_KEY = Key.create("VISITED_BUNDLES_KEY");

  private final NotNullLazyValue<InconsistentResourceBundleInspectionProvider[]> myInspectionProviders =
    new NotNullLazyValue<InconsistentResourceBundleInspectionProvider[]>() {
    @NotNull
    @Override
    protected InconsistentResourceBundleInspectionProvider[] compute() {
      return new InconsistentResourceBundleInspectionProvider[] {
        new PropertiesKeysConsistencyInspectionProvider(),
        new DuplicatedPropertiesInspectionProvider(),
        new MissingTranslationsInspectionProvider(),
        new PropertiesPlaceholdersInspectionProvider(),
        new InconsistentPropertiesEndsInspectionProvider(),
      };
    }
  };
  private final Map<String, Boolean> mySettings = new LinkedHashMap<>();

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inconsistent.resource.bundle.display.name");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "InconsistentResourceBundle";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(new OptionAccessor() {
      @Override
      public boolean getOption(String optionName) {
        return isProviderEnabled(optionName);
      }

      @Override
      public void setOption(String optionName, boolean optionValue) {
        if (optionValue) {
          mySettings.remove(optionName);
        }
        else {
          mySettings.put(optionName, false);
        }
      }
    });
    for (final InconsistentResourceBundleInspectionProvider provider : myInspectionProviders.getValue()) {
      panel.addCheckbox(provider.getPresentableName(), provider.getName());
    }
    return panel;
  }

  @Override
  public void writeSettings(final @NotNull Element node) throws WriteExternalException {
    for (final Map.Entry<String, Boolean> e : mySettings.entrySet()) {
      JDOMExternalizerUtil.writeField(node, e.getKey(), Boolean.toString(e.getValue()));
    }
  }

  @Override
  public void readSettings(final @NotNull Element node) throws InvalidDataException {
    mySettings.clear();
    for (final Object o : node.getChildren()) {
      if (o instanceof Element) {
        final Element e = (Element) o;
        final String name = e.getAttributeValue("name");
        final boolean value = Boolean.parseBoolean(e.getAttributeValue("value"));
        mySettings.put(name, value);
      }
    }
  }

  @Override
  public void inspectionStarted(@NotNull InspectionManager manager,
                                @NotNull GlobalInspectionContext globalContext,
                                @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.putUserData(VISITED_BUNDLES_KEY, new THashSet<>());
  }

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    Set<ResourceBundle> visitedBundles = globalContext.getUserData(VISITED_BUNDLES_KEY);
    if (!(file instanceof PropertiesFile)) return;
    final PropertiesFile propertiesFile = (PropertiesFile)file;
    ResourceBundle resourceBundle = propertiesFile.getResourceBundle();
    assert visitedBundles != null;
    if (!visitedBundles.add(resourceBundle)) return;
    List<PropertiesFile> files = resourceBundle.getPropertiesFiles();
    if (files.size() < 2) return;
    BidirectionalMap<PropertiesFile, PropertiesFile> parents = new BidirectionalMap<>();
    for (PropertiesFile f : files) {
      PropertiesFile parent = PropertiesUtil.getParent(f, files);
      if (parent != null) {
        parents.put(f, parent);
      }
    }
    final FactoryMap<PropertiesFile, Map<String,String>> propertiesFilesNamesMaps = new FactoryMap<PropertiesFile, Map<String, String>>() {
      @Nullable
      @Override
      protected Map<String, String> create(PropertiesFile key) {
        return key.getNamesMap();
      }
    };
    Map<PropertiesFile, Set<String>> keysUpToParent = new THashMap<>();
    for (PropertiesFile f : files) {
      Set<String> keys = new THashSet<>(propertiesFilesNamesMaps.get(f).keySet());
      PropertiesFile parent = parents.get(f);
      while (parent != null) {
        keys.addAll(propertiesFilesNamesMaps.get(parent).keySet());
        parent = parents.get(parent);
      }
      keysUpToParent.put(f, keys);
    }
    for (final InconsistentResourceBundleInspectionProvider provider : myInspectionProviders.getValue()) {
      if (isProviderEnabled(provider.getName())) {
        provider.check(parents, files, keysUpToParent, propertiesFilesNamesMaps, manager, globalContext.getRefManager(),
                       problemDescriptionsProcessor);
      }
    }
  }

  private boolean isProviderEnabled(final String providerName) {
    return ContainerUtil.getOrElse(mySettings, providerName, true);
  }

  @TestOnly
  public void enableProviders(final Class<? extends InconsistentResourceBundleInspectionProvider>... providerClasses) {
    Set<Class<? extends InconsistentResourceBundleInspectionProvider>> providersToEnable = ContainerUtil.newHashSet(providerClasses);
    for (InconsistentResourceBundleInspectionProvider inspectionProvider : myInspectionProviders.getValue()) {
      if (providersToEnable.contains(inspectionProvider.getClass())) {
        mySettings.put(inspectionProvider.getName(), true);
      }
    }
  }

  @TestOnly
  public void disableAllProviders() {
    for (InconsistentResourceBundleInspectionProvider inspectionProvider : myInspectionProviders.getValue()) {
      mySettings.put(inspectionProvider.getName(), false);
    }
  }

  @TestOnly
  public void clearSettings() {
    mySettings.clear();
  }
}