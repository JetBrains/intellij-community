// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization;

import com.intellij.platform.runtime.product.PluginModuleGroup;
import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.product.ProductModules;
import com.intellij.platform.runtime.product.impl.MainRuntimeModuleGroup;
import com.intellij.platform.runtime.product.impl.PluginModuleGroupImpl;
import com.intellij.platform.runtime.product.impl.ProductModulesImpl;
import com.intellij.platform.runtime.product.serialization.impl.ProductModulesXmlSerializer;
import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ProductModulesSerialization {
  private ProductModulesSerialization() { }

  public static @NotNull ProductModules loadProductModules(@NotNull Path xmlFile, @NotNull ProductMode currentMode,
                                                           @NotNull RuntimeModuleRepository repository) {
    try {
      return loadProductModules(Files.newInputStream(xmlFile), xmlFile.toString(), currentMode, repository);
    }
    catch (IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + xmlFile, e);
    }
  }

  public static @NotNull ProductModules loadProductModules(@NotNull InputStream inputStream, @NotNull String filePath,
                                                           @NotNull ProductMode currentMode,
                                                           @NotNull RuntimeModuleRepository repository) {
    return loadProductModules(inputStream, filePath, currentMode, repository, ResourceFileResolver.createDefault(repository));
  }

  public static @NotNull ProductModules loadProductModules(@NotNull InputStream inputStream,
                                                           @NotNull String filePath,
                                                           @NotNull ProductMode currentMode,
                                                           @NotNull RuntimeModuleRepository repository,
                                                           @NotNull ResourceFileResolver resourceFileResolver) {
    RawProductModules rawProductModules = readProductModulesAndMergeIncluded(inputStream, filePath, resourceFileResolver);
    return loadProductModules(rawProductModules, filePath, currentMode, repository, resourceFileResolver);
  }

  public static @NotNull RawProductModules readProductModulesAndMergeIncluded(@NotNull InputStream inputStream, @NotNull String filePath,
                                                                              @NotNull ResourceFileResolver resolver) {
    try {
      RawProductModules rawProductModules = ProductModulesXmlSerializer.parseModuleXml(inputStream);
      if (rawProductModules.getIncludedFrom().isEmpty()) {
        return rawProductModules;
      }
      
      Set<RawIncludedRuntimeModule> allMainGroupModules = new LinkedHashSet<>(rawProductModules.getMainGroupModules());
      Set<RuntimeModuleId> allBundledPluginMainModules = new LinkedHashSet<>(rawProductModules.getBundledPluginMainModules());
      mergeIncludedFiles(rawProductModules, filePath, resolver, allMainGroupModules, allBundledPluginMainModules);
      return new RawProductModules(new ArrayList<>(allMainGroupModules), new ArrayList<>(allBundledPluginMainModules), 
                                   Collections.emptyList());
    }
    catch (XMLStreamException | IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + filePath, e);
    }
  }

  private static @NotNull ProductModulesImpl loadProductModules(@NotNull RawProductModules rawProductModules,
                                                                @NotNull String debugName,
                                                                @NotNull ProductMode currentMode,
                                                                @NotNull RuntimeModuleRepository repository,
                                                                @NotNull ResourceFileResolver resourceFileResolver) {

    MainRuntimeModuleGroup mainGroup = new MainRuntimeModuleGroup(rawProductModules.getMainGroupModules(), currentMode, repository);
    List<PluginModuleGroup> bundledPluginModuleGroups = new ArrayList<>();
    for (RuntimeModuleId pluginMainModule : rawProductModules.getBundledPluginMainModules()) {
      RuntimeModuleDescriptor module = repository.resolveModule(pluginMainModule).getResolvedModule();
      /* todo: this check is temporarily added for JetBrains Client; 
         It includes intellij.performanceTesting.async plugin which dependencies aren't available in all IDEs, so we need to skip it.
         Plugins should define which modules from them should be included into JetBrains Client instead. */
      if (module != null) {
        bundledPluginModuleGroups.add(new PluginModuleGroupImpl(module, currentMode, repository, resourceFileResolver));
      }
    }
    return new ProductModulesImpl(debugName, mainGroup, bundledPluginModuleGroups);
  }

  private static void mergeIncludedFiles(@NotNull RawProductModules rawProductModules,
                                         @NotNull String debugName,
                                         @NotNull ResourceFileResolver resolver,
                                         @NotNull Set<RawIncludedRuntimeModule> mainGroupModules,
                                         @NotNull Set<RuntimeModuleId> bundledPluginMainModules) throws IOException, XMLStreamException {
    for (RawIncludedFromData includedFromData : rawProductModules.getIncludedFrom()) {
      RuntimeModuleId includedId = includedFromData.getFromModule();
      InputStream inputStream = resolver.readResourceFile(includedId, "META-INF/" + includedId.getStringId() + "/product-modules.xml");
      if (inputStream == null) {
        throw new MalformedRepositoryException("'" + includedId.getStringId() + "' included in " +
                                               debugName + " doesn't contain product-modules.xml");
      }
      RawProductModules includedModules = ProductModulesXmlSerializer.parseModuleXml(inputStream);
      mergeIncludedFiles(includedModules, debugName, resolver, mainGroupModules, bundledPluginMainModules);
      for (RawIncludedRuntimeModule mainGroupModule : includedModules.getMainGroupModules()) {
        if (!includedFromData.getWithoutModules().contains(mainGroupModule.getModuleId())) {
          mainGroupModules.add(mainGroupModule);
        }
      }
      for (RuntimeModuleId pluginMainModule : includedModules.getBundledPluginMainModules()) {
        if (!includedFromData.getWithoutModules().contains(pluginMainModule)) {
          bundledPluginMainModules.add(pluginMainModule);
        }
      }
    }
  }
}
