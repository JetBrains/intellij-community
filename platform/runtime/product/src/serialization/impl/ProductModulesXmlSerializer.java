// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization.impl;

import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.product.serialization.RawIncludedFromData;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.product.serialization.RawProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.*;

public final class ProductModulesXmlSerializer {
  public static @NotNull RawProductModules parseModuleXml(@NotNull InputStream inputStream) throws XMLStreamException {
    XMLStreamReader reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream);
    int level = 0;
    RuntimeModuleLoadingRule loadingRule = null;
    String moduleName = null;
    List<RawIncludedRuntimeModule> rootMainGroupModules = new ArrayList<>();
    List<RuntimeModuleId> bundledPluginMainModules = new ArrayList<>();
    List<RawIncludedFromData> includedFrom = new ArrayList<>();
    RuntimeModuleId includedFromModule = null;
    Set<RuntimeModuleId> withoutModules = new LinkedHashSet<>();
    String secondLevelTag = null;
    String thirdLevelTag = null;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        level++;
        String tagName = reader.getLocalName();
        if (level == 2) {
          secondLevelTag = tagName;
        }
        else if (level == 3) {
          thirdLevelTag = tagName;
          if (tagName.equals("module")) {
            if (reader.getAttributeCount() > 0) {
              String attributeName = reader.getAttributeLocalName(0);
              if (!"loading".equals(attributeName)) {
                throw new XMLStreamException("Unexpected attribute '" + attributeName + "'");
              }
              String loadingRuleString = reader.getAttributeValue(0);
              switch (loadingRuleString) {
                case "optional": loadingRule = RuntimeModuleLoadingRule.OPTIONAL; break;
                case "required": loadingRule = RuntimeModuleLoadingRule.REQUIRED; break;
                case "on-demand": loadingRule = RuntimeModuleLoadingRule.ON_DEMAND; break;
                default: throw new XMLStreamException("Unknown loading rule '" + loadingRuleString + "'");
              }
            }
            else {
              loadingRule = RuntimeModuleLoadingRule.REQUIRED;
            }
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
          if ("main-root-modules".equals(secondLevelTag)) {
            assert loadingRule != null;
            rootMainGroupModules.add(new RawIncludedRuntimeModule(moduleId, loadingRule));
          }
          else if ("bundled-plugins".equals(secondLevelTag)) {
            bundledPluginMainModules.add(moduleId);
          }
          else if ("include".equals(secondLevelTag)) {
            if ("from-module".equals(thirdLevelTag)) {
              includedFromModule = moduleId;
            }
            else if ("without-module".equals(thirdLevelTag)) {
              withoutModules.add(moduleId);
            }
            else {
              throw new XMLStreamException("Unexpected sub tag in 'include': " + secondLevelTag);
            }
          }
          else {
            throw new XMLStreamException("Unexpected second-level tag " + secondLevelTag);
          }
          moduleName = null;
          loadingRule = null;
        }
        else if (level == 1 && "include".equals(secondLevelTag)) {
          if (includedFromModule == null) {
            throw new XMLStreamException("'from-module' tag for 'include' is not specified");
          }
          includedFrom.add(new RawIncludedFromData(includedFromModule, withoutModules));
          includedFromModule = null;
          withoutModules = new LinkedHashSet<>();
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
