// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.repository.impl.ProductModulesImpl;
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ProductModulesXmlLoader {
  public static @NotNull ProductModules parseModuleXml(@NotNull InputStream inputStream, @NotNull String debugName, @NotNull RuntimeModuleRepository repository) throws XMLStreamException {
    XMLStreamReader reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream);
    int level = 0;
    ModuleImportance importance = null;
    String moduleName = null;
    List<IncludedRuntimeModule> platformModules = new ArrayList<>();
    List<RuntimeModuleDescriptor> bundledPluginModules = new ArrayList<>();
    String parentTag = null;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        level++;
        String tagName = reader.getLocalName();
        if (level == 2) {
          parentTag = tagName;
        }
        else if (level == 3 && tagName.equals("module")) {
          if (reader.getAttributeCount() > 0) {
            String attributeName = reader.getAttributeLocalName(0);
            if (!"importance".equals(attributeName)) {
              throw new XMLStreamException("Unexpected attribute '" + attributeName + "'");
            }
            importance = ModuleImportance.valueOf(reader.getAttributeValue(0).toUpperCase(Locale.US));
          }
          else {
            importance = ModuleImportance.FUNCTIONAL;
          }
        }
      }
      else if (event == XMLStreamConstants.CHARACTERS) {
        if (level == 3) {
          moduleName = reader.getText().trim();
        }
      }
      else if (event == XMLStreamConstants.END_ELEMENT) {
        level--;
        if (level == 2) {
          if (moduleName == null || moduleName.isEmpty()) {
            throw new XMLStreamException("Module name is not specified");
          }
          if ("root-platform-modules".equals(parentTag)) {
            assert importance != null;
            platformModules.add(new IncludedRuntimeModuleImpl(repository.getModule(RuntimeModuleId.raw(moduleName)), importance, Collections.emptySet()));
          }
          else {
            bundledPluginModules.add(repository.getModule(RuntimeModuleId.raw(moduleName)));
          }
          moduleName = null;
          importance = null;
        }
        if (level == 0) {
          break;
        }
      }
    }
    reader.close();
    return new ProductModulesImpl(debugName, platformModules, bundledPluginModules);
  }
}
