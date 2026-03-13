// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule;
import com.intellij.platform.runtime.repository.serialization.RawIncludedRuntimeModule;
import com.intellij.platform.runtime.repository.serialization.RawRuntimePluginHeader;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.PrintWriter;

import static com.intellij.platform.runtime.repository.serialization.impl.ModuleXmlSerializer.writeEolAndIndent;

final class PluginHeaderXmlSerializer {
  static void writePluginHeaderXml(RawRuntimePluginHeader header, PrintWriter output, XMLOutputFactory factory) throws XMLStreamException {
    XMLStreamWriter writer = factory.createXMLStreamWriter(output);
    writer.writeStartDocument("UTF-8", "1.0");
    writeEolAndIndent(writer, 0);
    writer.writeComment(" The IDE doesn't use this file; it takes data from module-descriptors.dat instead ");
    writeEolAndIndent(writer, 0);
    writer.writeStartElement("plugin");
    writer.writeAttribute("id", header.getPluginId());
    writeEolAndIndent(writer, 1);
    writer.writeEmptyElement("plugin-descriptor-module");
    writer.writeAttribute("name", header.getPluginDescriptorModuleId().getName());
    writer.writeAttribute("namespace", header.getPluginDescriptorModuleId().getNamespace());
    for (RawIncludedRuntimeModule module : header.getIncludedModules()) {
      writeEolAndIndent(writer, 1);
      writer.writeEmptyElement("module");
      writer.writeAttribute("name", module.getModuleId().getName());
      writer.writeAttribute("namespace", module.getModuleId().getNamespace());
      writer.writeAttribute("loading", convertLoadingRuleToString(module.getLoadingRule()));
      RuntimeModuleId requiredIfAvailableId = module.getRequiredIfAvailableId();
      if (requiredIfAvailableId != null) {
        writer.writeAttribute("required-if-available", requiredIfAvailableId.getName());
      }
    }
    writeEolAndIndent(writer, 0);
    writer.writeEndElement();
    writer.writeEndDocument();
    output.flush();
  }

  private static @NotNull String convertLoadingRuleToString(@NotNull RuntimeModuleLoadingRule loadingRule) {
    switch (loadingRule) {
      case REQUIRED: return "required";
      case OPTIONAL: return "optional";
      case EMBEDDED: return "embedded";
      case ON_DEMAND: return "on-demand";
    }
    throw new IllegalArgumentException("Unknown loading rule: " + loadingRule);
  }
}
