// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.i18n.inconsistentResourceBundle;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

public final class InconsistentResourceBundleInspection extends GlobalSimpleInspectionTool {
  private static final Key<Set<ResourceBundle>> VISITED_BUNDLES_KEY = Key.create("VISITED_BUNDLES_KEY");

  private final NotNullLazyValue<InconsistentResourceBundleInspectionProvider[]> myInspectionProviders = NotNullLazyValue.lazy(() -> {
    return new InconsistentResourceBundleInspectionProvider[]{
      new PropertiesKeysConsistencyInspectionProvider(),
      new DuplicatedPropertiesInspectionProvider(),
      new MissingTranslationsInspectionProvider(),
      new PropertiesPlaceholdersInspectionProvider(),
      new InconsistentPropertiesEndsInspectionProvider(),
    };
  });
  private final Map<String, Boolean> mySettings = new LinkedHashMap<>();


  @Override
  public @NotNull String getShortName() {
    return "InconsistentResourceBundle";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return new OptPane(ContainerUtil.map(
      myInspectionProviders.getValue(),
      provider -> OptPane.checkbox(provider.getName(), provider.getPresentableName())));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return OptionController.of(this::isProviderEnabled, (bindId, value) -> {
      boolean boolValue = (Boolean)value;
      if (boolValue) {
        mySettings.remove(bindId);
      }
      else {
        mySettings.put(bindId, false);
      }
    });
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
    for (final Element e : node.getChildren()) {
      final String name = e.getAttributeValue("name");
      final boolean value = Boolean.parseBoolean(e.getAttributeValue("value"));
      mySettings.put(name, value);
    }
  }

  @Override
  public void inspectionStarted(@NotNull InspectionManager manager,
                                @NotNull GlobalInspectionContext globalContext,
                                @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    globalContext.putUserData(VISITED_BUNDLES_KEY, ConcurrentCollectionFactory.createConcurrentSet());
  }

  @Override
  public void checkFile(@NotNull PsiFile psiFile,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    Set<ResourceBundle> visitedBundles = globalContext.getUserData(VISITED_BUNDLES_KEY);
    if (!(psiFile instanceof PropertiesFile propertiesFile)) return;
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
    final Map<PropertiesFile, Map<String, String>> propertiesFilesNamesMaps = FactoryMap.create(key -> key.getNamesMap());
    Map<PropertiesFile, Set<String>> keysUpToParent = new HashMap<>();
    for (PropertiesFile f : files) {
      Set<String> keys = new HashSet<>(propertiesFilesNamesMaps.get(f).keySet());
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
    return mySettings.getOrDefault(providerName, true);
  }

  @SafeVarargs
  @TestOnly
  public final void enableProviders(final Class<? extends InconsistentResourceBundleInspectionProvider>... providerClasses) {
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