// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public final class PluginXmlReader {
  private static final String PLUGIN_XML_PATH = "META-INF/plugin.xml";

  @NotNull
  public static List<IncludedRuntimeModule> loadPluginModules(RuntimeModuleDescriptor mainModule, RuntimeModuleRepository repository) {
    try {
      List<IncludedRuntimeModule> modules = new ArrayList<>();
      Set<String> addedModules = new HashSet<>();
      modules.add(new IncludedRuntimeModuleImpl(mainModule, ModuleImportance.FUNCTIONAL, Collections.emptySet()));
      addedModules.add(mainModule.getModuleId().getStringId());
      try (InputStream inputStream = mainModule.readFile(PLUGIN_XML_PATH)) {
        if (inputStream == null) {
          throw new MalformedRepositoryException(PLUGIN_XML_PATH + " is not found in '" + mainModule.getModuleId().getStringId() + "' module in " + repository);
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
                RuntimeModuleDescriptor module = repository.resolveModule(RuntimeModuleId.raw(moduleName)).getResolvedModule();
                //todo it makes sense to print something to log if optional module cannot be loaded
                if (module != null) {
                  modules.add(new IncludedRuntimeModuleImpl(module, ModuleImportance.OPTIONAL, Collections.emptySet()));
                }
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
