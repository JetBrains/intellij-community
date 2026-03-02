// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

final class ModuleXmlSerializer {
  private static final String MODULE_TAG = "module";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String NAMESPACE_ATTRIBUTE = "namespace";
  private static final String VISIBILITY_ATTRIBUTE = "visibility";
  private static final String RESOURCE_ROOT_TAG = "resource-root";
  private static final String PATH_ATTRIBUTE = "path";

  public static void writeModuleXml(@NotNull RawRuntimeModuleDescriptor descriptor, @NotNull PrintWriter output, XMLOutputFactory factory)
    throws XMLStreamException {
    XMLStreamWriter writer = factory.createXMLStreamWriter(output);
    writer.writeStartDocument("UTF-8", "1.0");
    writeEolAndIndent(writer, 0);
    writer.writeStartElement(MODULE_TAG);
    writer.writeAttribute(NAME_ATTRIBUTE, descriptor.getModuleId().getName());
    writer.writeAttribute(NAMESPACE_ATTRIBUTE, descriptor.getModuleId().getNamespace());
    writer.writeAttribute(VISIBILITY_ATTRIBUTE, convertToString(descriptor.getVisibility()));
    List<RuntimeModuleId> dependencies = descriptor.getDependencyIds();
    if (!dependencies.isEmpty()) {
      writeEolAndIndent(writer, 1);
      writer.writeStartElement("dependencies");
      for (RuntimeModuleId dependency : dependencies) {
        writeEolAndIndent(writer, 2);
        writer.writeEmptyElement(MODULE_TAG);
        writer.writeAttribute(NAME_ATTRIBUTE, dependency.getName());
        if (!dependency.getNamespace().equals(RuntimeModuleId.DEFAULT_NAMESPACE)) {
          writer.writeAttribute(NAMESPACE_ATTRIBUTE, dependency.getNamespace());
        }
      }
      writeEolAndIndent(writer, 1);
      writer.writeEndElement();
    }
    List<String> roots = descriptor.getResourcePaths();
    if (!roots.isEmpty()) {
      writeEolAndIndent(writer, 1);
      writer.writeStartElement("resources");
      for (String root : roots) {
        writeEolAndIndent(writer, 2);
        writer.writeEmptyElement(RESOURCE_ROOT_TAG);
        writer.writeAttribute(PATH_ATTRIBUTE, root);
      }
      writeEolAndIndent(writer, 1);
      writer.writeEndElement();
    }
    writeEolAndIndent(writer, 0);
    writer.writeEndElement();
    writer.writeEndDocument();
    output.flush();
  }

  private static String convertToString(@NotNull RuntimeModuleVisibility visibility) {
    switch (visibility) {
      case PUBLIC: return "public";
      case PRIVATE: return "private";
      case INTERNAL: return "internal";
      default: throw new IllegalArgumentException("Unknown visibility: " + visibility);
    }
  }

  static void writeEolAndIndent(XMLStreamWriter writer, int indent) throws XMLStreamException {
    writer.writeCharacters("\n");
    for (int i = 0; i < indent; i++) {
      writer.writeCharacters("  ");
    }
  }

  public static RawRuntimeModuleDescriptor parseModuleXml(XMLInputFactory factory, InputStream inputStream) throws XMLStreamException {
    XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
    ArrayList<RuntimeModuleId> dependencies = new ArrayList<>();
    ArrayList<String> resources = new ArrayList<>();
    String moduleName = null;
    String moduleNamespace = RuntimeModuleId.DEFAULT_NAMESPACE;
    String visibilityString = null;
    int level = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        level++;
        String tagName = reader.getLocalName();
        if (level == 1 && tagName.equals(MODULE_TAG)) {
          moduleName = XmlStreamUtil.readFirstAttribute(reader, NAME_ATTRIBUTE);
          if (reader.getAttributeCount() > 1 && reader.getAttributeLocalName(1).equals(NAMESPACE_ATTRIBUTE)) {
            moduleNamespace = reader.getAttributeValue(1);
          }
          if (reader.getAttributeCount() > 2 && reader.getAttributeLocalName(2).equals(VISIBILITY_ATTRIBUTE)) {
            visibilityString = reader.getAttributeValue(2);
          }
        }
        else if (level == 3 & tagName.equals(MODULE_TAG)) {
          String dependencyName = XmlStreamUtil.readFirstAttribute(reader, NAME_ATTRIBUTE);
          String dependencyNamespace =
            reader.getAttributeCount() > 1 && reader.getAttributeLocalName(1).equals(NAMESPACE_ATTRIBUTE) ? reader.getAttributeValue(1) : RuntimeModuleId.DEFAULT_NAMESPACE;
          dependencies.add(RuntimeModuleId.raw(dependencyName, dependencyNamespace));
        }
        else if (level == 3 && tagName.equals(RESOURCE_ROOT_TAG)) {
          String relativePath = XmlStreamUtil.readFirstAttribute(reader, PATH_ATTRIBUTE);
          resources.add(relativePath);
        }
      }
      else if (event == XMLStreamConstants.END_ELEMENT) {
        level--;
        if (level == 0) {
          break;
        }
      }
    }
    reader.close();
    if (moduleName == null) {
      throw new XMLStreamException("Required attribute 'module' is not specified");
    }
    RuntimeModuleVisibility visibility = parseVisibility(visibilityString);
    return RawRuntimeModuleDescriptor.create(RuntimeModuleId.raw(moduleName, moduleNamespace), visibility, resources, dependencies);
  }

  private static RuntimeModuleVisibility parseVisibility(@Nullable String visibilityString) {
    if (visibilityString == null) return RuntimeModuleVisibility.PRIVATE;
    switch (visibilityString) {
      case "public": return RuntimeModuleVisibility.PUBLIC;
      case "private": return RuntimeModuleVisibility.PRIVATE;
      case "internal": return RuntimeModuleVisibility.INTERNAL;
    }
    return RuntimeModuleVisibility.PRIVATE;
  }
}
