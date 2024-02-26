// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization.impl;

import com.intellij.platform.runtime.product.ModuleImportance;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.product.serialization.RawProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductModulesXmlSerializer {
  public static @NotNull RawProductModules parseModuleXml(@NotNull InputStream inputStream) throws XMLStreamException {
    XMLStreamReader reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream);
    int level = 0;
    ModuleImportance importance = null;
    String moduleName = null;
    List<RawIncludedRuntimeModule> rootMainGroupModules = new ArrayList<>();
    List<RuntimeModuleId> bundledPluginMainModules = new ArrayList<>();
    List<RuntimeModuleId> includedFrom = new ArrayList<>();
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
          RuntimeModuleId moduleId = RuntimeModuleId.raw(moduleName);
          if ("main-root-modules".equals(parentTag)) {
            assert importance != null;
            rootMainGroupModules.add(new RawIncludedRuntimeModule(moduleId, importance));
          }
          else if ("bundled-plugins".equals(parentTag)) {
            bundledPluginMainModules.add(moduleId);
          }
          else if ("include".equals(parentTag)) {
            includedFrom.add(moduleId);
          }
          else {
            throw new XMLStreamException("Unexpected second-level tag " + parentTag);
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
    return new RawProductModules(rootMainGroupModules, bundledPluginMainModules, includedFrom);
  }
}
