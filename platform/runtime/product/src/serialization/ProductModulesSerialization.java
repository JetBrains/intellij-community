// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.product.ProductMode;
import com.intellij.platform.runtime.product.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.product.serialization.impl.ProductModulesXmlLoader;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProductModulesSerialization {
  public static @NotNull ProductModules loadProductModules(@NotNull Path xmlFile, @NotNull ProductMode currentMode,
                                                           @NotNull RuntimeModuleRepository repository) {
    try {
      return loadProductModules(Files.newInputStream(xmlFile), xmlFile.toString(), currentMode, repository);
    }
    catch (IOException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + xmlFile, e);
    }
  }

  @NotNull
  public static ProductModules loadProductModules(@NotNull InputStream inputStream, @NotNull String filePath,
                                                  @NotNull ProductMode currentMode,
                                                  @NotNull RuntimeModuleRepository repository) {
    try {
      return ProductModulesXmlLoader.parseModuleXml(inputStream, filePath, currentMode, repository);
    }
    catch (XMLStreamException e) {
      throw new MalformedRepositoryException("Failed to load module group from " + filePath, e);
    }
  }
}
