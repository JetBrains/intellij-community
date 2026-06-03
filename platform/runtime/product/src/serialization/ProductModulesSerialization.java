// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization;

import com.intellij.platform.runtime.product.ProductModules;
import com.intellij.platform.runtime.product.impl.ProductModulesImpl;
import com.intellij.platform.runtime.product.serialization.impl.ProductModulesXmlSerializer;
import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProductModulesSerialization {
  private ProductModulesSerialization() { }

  public static @NotNull ProductModules loadProductModules(@NotNull Path xmlFile,
                                                           @NotNull RuntimeModuleRepository repository) {
    try {
      return loadProductModules(Files.newInputStream(xmlFile), xmlFile.toString(), repository);
    }
    catch (IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + xmlFile, e);
    }
  }

  public static @NotNull ProductModules loadProductModules(@NotNull InputStream inputStream, @NotNull String filePath,
                                                           @NotNull RuntimeModuleRepository repository) {
    return loadProductModules(inputStream, filePath, ResourceFileResolver.createDefault(repository));
  }

  public static @NotNull ProductModules loadProductModules(@NotNull InputStream inputStream,
                                                           @NotNull String filePath,
                                                           @NotNull ResourceFileResolver resourceFileResolver) {
    RawProductModules rawProductModules = readProductModulesAndMergeIncluded(inputStream, filePath, resourceFileResolver);
    return loadProductModules(rawProductModules, filePath);
  }

  public static @NotNull RawProductModules readProductModulesAndMergeIncluded(@NotNull InputStream inputStream, @NotNull String filePath,
                                                                              @NotNull ResourceFileResolver resolver) {
    try {
      RawProductModules rawProductModules = ProductModulesXmlSerializer.parseModuleXml(inputStream);
      if (rawProductModules.getIncludedFrom().isEmpty()) {
        return rawProductModules;
      }
      
      Set<RuntimeModuleId> allBundledPluginMainModules = new LinkedHashSet<>(rawProductModules.getBundledPluginMainModules());
      mergeIncludedFiles(rawProductModules, filePath, resolver, allBundledPluginMainModules, Collections.emptySet());
      return new RawProductModules(new ArrayList<>(allBundledPluginMainModules), Collections.emptyList());
    }
    catch (XMLStreamException | IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + filePath, e);
    }
  }

  private static @NotNull ProductModulesImpl loadProductModules(@NotNull RawProductModules rawProductModules,
                                                                @NotNull String debugName) {
    return new ProductModulesImpl(debugName, rawProductModules.getBundledPluginMainModules());
  }

  private static void mergeIncludedFiles(@NotNull RawProductModules rawProductModules,
                                         @NotNull String debugName,
                                         @NotNull ResourceFileResolver resolver,
                                         @NotNull Set<RuntimeModuleId> bundledPluginMainModules,
                                         @NotNull Set<RuntimeModuleId> withoutModules) throws IOException, XMLStreamException {
    for (RawIncludedFromData includedFromData : rawProductModules.getIncludedFrom()) {
      RuntimeModuleId includedId = includedFromData.getFromModule();
      InputStream inputStream = resolver.readResourceFile(includedId, "META-INF/" + includedId.getName() + "/product-modules.xml");
      if (inputStream == null) {
        throw new MalformedRepositoryException("'" + includedId.getDisplayName() + "' included in " +
                                               debugName + " doesn't contain product-modules.xml");
      }
      RawProductModules includedModules = ProductModulesXmlSerializer.parseModuleXml(inputStream);
      var withoutIncluding = new HashSet<>(withoutModules);
      withoutIncluding.addAll(includedFromData.getWithoutModules());
      for (RuntimeModuleId module : includedFromData.getWithoutModules()) {
        if (module.getNamespace().endsWith(RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX)) {
          withoutIncluding.add(RuntimeModuleId.contentModule(module.getName(), RuntimeModuleId.DEFAULT_NAMESPACE));
        }
      }
      mergeIncludedFiles(includedModules, debugName, resolver, bundledPluginMainModules, withoutIncluding);
      for (RuntimeModuleId pluginMainModule : includedModules.getBundledPluginMainModules()) {
        if (!withoutIncluding.contains(pluginMainModule)) {
          bundledPluginMainModules.add(pluginMainModule);
        }
      }
    }
  }
}
