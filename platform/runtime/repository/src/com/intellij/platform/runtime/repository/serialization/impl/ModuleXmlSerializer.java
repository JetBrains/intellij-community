// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization.impl;

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import org.jetbrains.annotations.NotNull;

import javax.xml.stream.*;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

final class ModuleXmlSerializer {
  private static final String MODULE_TAG = "module";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String RESOURCE_ROOT_TAG = "resource-root";
  private static final String PATH_ATTRIBUTE = "path";

  public static void writeModuleXml(@NotNull RawRuntimeModuleDescriptor descriptor, @NotNull PrintWriter output, XMLOutputFactory factory)
    throws XMLStreamException {
    XMLStreamWriter writer = factory.createXMLStreamWriter(output);
    writer.writeStartDocument("UTF-8", "1.0");
    writeEolAndIndent(writer, 0);
    writer.writeStartElement(MODULE_TAG);
    writer.writeAttribute(NAME_ATTRIBUTE, descriptor.getId());
    List<String> dependencies = descriptor.getDependencies();
    if (!dependencies.isEmpty()) {
      writeEolAndIndent(writer, 1);
      writer.writeStartElement("dependencies");
      for (String dependency : dependencies) {
        writeEolAndIndent(writer, 2);
        writer.writeEmptyElement(MODULE_TAG);
        writer.writeAttribute(NAME_ATTRIBUTE, dependency);
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

  private static void writeEolAndIndent(XMLStreamWriter writer, int indent) throws XMLStreamException {
    writer.writeCharacters("\n");
    for (int i = 0; i < indent; i++) {
      writer.writeCharacters("  ");
    }
  }

  public static RawRuntimeModuleDescriptor parseModuleXml(XMLInputFactory factory, InputStream inputStream) throws XMLStreamException {
    XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
    ArrayList<String> dependencies = new ArrayList<>();
    ArrayList<String> resources = new ArrayList<>();
    String moduleName = null;
    int level = 0;
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        level++;
        String tagName = reader.getLocalName();
        if (level == 1 && tagName.equals(MODULE_TAG)) {
          moduleName = XmlStreamUtil.readFirstAttribute(reader, NAME_ATTRIBUTE);
        }
        else if (level == 3 & tagName.equals(MODULE_TAG)) {
          dependencies.add(XmlStreamUtil.readFirstAttribute(reader, NAME_ATTRIBUTE));
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
    return new RawRuntimeModuleDescriptor(moduleName, resources, dependencies);
  }
}
