// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization.impl;

import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.product.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver;
import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.serialization.impl.XmlStreamUtil;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PluginXmlReader {
  private static final String PLUGIN_XML_PATH = "META-INF/plugin.xml";

  public static @NotNull List<RawIncludedRuntimeModule> loadPluginModules(RuntimeModuleDescriptor mainModule, RuntimeModuleRepository repository,
                                                                          ResourceFileResolver resourceFileResolver) {
    try {
      List<RawIncludedRuntimeModule> modules = new ArrayList<>();
      Set<String> addedModules = new HashSet<>();
      modules.add(new RawIncludedRuntimeModule(mainModule.getModuleId(), RuntimeModuleLoadingRule.REQUIRED));
      addedModules.add(mainModule.getModuleId().getStringId());
      try (InputStream inputStream = resourceFileResolver.readResourceFile(mainModule.getModuleId(), PLUGIN_XML_PATH)) {
        if (inputStream == null) {
          throw new MalformedRepositoryException(PLUGIN_XML_PATH + " is not found in '" + mainModule.getModuleId().getStringId() + "' module in " 
                                                 + repository + " using " + resourceFileResolver + "; resources roots: " + mainModule.getResourceRootPaths());
        }
        XMLStreamReader reader = XMLInputFactory.newDefaultFactory().createXMLStreamReader(inputStream);
        int level = 0;
        boolean inContentTag = false;
        while (reader.hasNext()) {
          int event = reader.next();
          if (event == XMLStreamConstants.START_ELEMENT) {
            level++;
            String tagName = reader.getLocalName();
            if (level == 2 && tagName.equals("content")) {
              inContentTag = true;
            }
            else if (level == 3 && inContentTag && tagName.equals("module")) {
              String moduleAttribute = XmlStreamUtil.readFirstAttribute(reader, "name");
              int moduleNameEnd = moduleAttribute.indexOf('/');
              String moduleName = moduleNameEnd == -1 ? moduleAttribute : moduleAttribute.substring(0, moduleNameEnd);
              if (addedModules.add(moduleName)) {
                modules.add(new RawIncludedRuntimeModule(RuntimeModuleId.raw(moduleName), RuntimeModuleLoadingRule.OPTIONAL));
              }
            }
          }
          else if (event == XMLStreamConstants.END_ELEMENT) {
            level--;
            if (level == 0 || level == 1 && inContentTag) {
              break;
            }
          }
        }
      }
      return modules;
    }
    catch (IOException | XMLStreamException e) {
      throw new MalformedRepositoryException("Failed to load included modules for " + mainModule.getModuleId().getStringId(), e);
    }
  }
}
