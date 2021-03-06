// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.generator;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.CharArrayReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("HardCodedStringLiteral")
public class ModelGen {
  private final ModelDesc model = new ModelDesc();
  private final Map<String, String> schemaLocationMap = new HashMap<>();
  private final ModelLoader loader;
  private final Emitter emitter;
  private final FileManager fileManager;


  public ModelGen(ModelLoader loader) {
    this(loader, new JetBrainsEmitter(), new MergingFileManager());
  }

  public ModelGen(ModelLoader loader, Emitter emitter, FileManager fileManager) {
    this.loader = loader;
    this.emitter = emitter;
    this.fileManager = fileManager;
  }

  public ModelDesc getModel() {
    return model;
  }

  public static Element loadXml(File configXml) throws Exception {
    SAXBuilder saxBuilder = new SAXBuilder();
    saxBuilder.setEntityResolver(new EntityResolver() {
      @Override
      public InputSource resolveEntity(String publicId,
                                       String systemId)
              throws SAXException, IOException {
        return new InputSource(new CharArrayReader(new char[0]));
      }
    });
    final Document document = saxBuilder.build(configXml);
    return document.getRootElement();
  }

  public void loadConfig(File configXml) throws Exception {
    loadConfig(loadXml(configXml));
  }

  public void setConfig(String schema, String location, NamespaceDesc desc, String... schemasToSkip) {
    schemaLocationMap.put(schema, location);
    for (String sch : schemasToSkip) {
      if (sch != null && sch.length() > 0) {
        model.nsdMap.put(sch, new NamespaceDesc(sch));
      }
    }
    model.nsdMap.put("", new NamespaceDesc("", "", "com.intellij.util.xml.DomElement", "", null, null, null, null));
    model.nsdMap.put(desc.name, desc);
  }

  public void loadConfig(Element element) {
    final Element namespaceEl = element.getChild("namespaces");
    for (Element e : namespaceEl.getChildren("schemaLocation")) {
      final String name = e.getAttributeValue("name");
      final String file = e.getAttributeValue("file");
      schemaLocationMap.put(name, file);
    }
    for (Element e : namespaceEl.getChildren("reserved-name")) {
      final String name = e.getAttributeValue("name");
      final String replacement = e.getAttributeValue("replace-with");
      model.name2replaceMap.put(name, replacement);
    }
    NamespaceDesc def = new NamespaceDesc("", "generated", "java.lang.Object", "", null, null, null, null);
    for (Element nsElement : namespaceEl.getChildren("namespace")) {
      final String name = nsElement.getAttributeValue("name");
      final NamespaceDesc nsDesc = new NamespaceDesc(name, def);

      final String skip = nsElement.getAttributeValue("skip");
      final String prefix = nsElement.getAttributeValue("prefix");
      final String superC = nsElement.getAttributeValue("super");
      final String imports = nsElement.getAttributeValue("imports");
      final String packageS = nsElement.getAttributeValue("package");
      final String packageEnumS = nsElement.getAttributeValue("enums");
      final String interfaces = nsElement.getAttributeValue("interfaces");
      final ArrayList<String> list = new ArrayList<>();
      for (Element pkgElement : nsElement.getChildren("package")) {
        final String pkgName = pkgElement.getAttributeValue("name");
        final String fileName = pkgElement.getAttributeValue("file");
        list.add(fileName);
        list.add(pkgName);
      }
      for (Element pkgElement : nsElement.getChildren("property")) {
        final String propertyName = pkgElement.getAttributeValue("name");
        final String propertyValue = pkgElement.getAttributeValue("value");
        nsDesc.props.put(propertyName, propertyValue);
      }

      if (skip != null) nsDesc.skip = skip.equalsIgnoreCase("true");
      if (prefix != null) nsDesc.prefix = prefix;
      if (superC != null) nsDesc.superClass = superC;
      if (imports != null) nsDesc.imports = imports;
      if (packageS != null) nsDesc.pkgName = packageS;
      if (packageEnumS != null) nsDesc.enumPkg = packageEnumS;
      if (interfaces != null) nsDesc.intfs = interfaces;
      if (!list.isEmpty()) nsDesc.pkgNames = ArrayUtilRt.toStringArray(list);
      if (name.length() == 0) def = nsDesc;
      model.nsdMap.put(name, nsDesc);
    }
  }

  public void perform(final File outputRoot, final File... modelRoots) throws Exception {
    loadModel(modelRoots);
    emitter.emit(fileManager, model, outputRoot);

    Util.log("Done.");
  }

  public void loadModel(final File... modelRoots) throws Exception {
    XMLEntityResolver resolver = new XMLEntityResolver() {
      @Override
      public XMLInputSource resolveEntity(XMLResourceIdentifier xmlResourceIdentifier) throws XNIException, IOException {
        String esid = xmlResourceIdentifier.getExpandedSystemId();
        if (esid == null) {
          final String location = schemaLocationMap.get(xmlResourceIdentifier.getNamespace());
          if (location != null) {
            esid = location;
          } else {
            return null;
          }
        }
        // Util.log("resolving "+esid);
        File f = null;
        for (File root : modelRoots) {
          if (root == null) continue;
          if (root.isDirectory()) {
            final String fileName = esid.substring(esid.lastIndexOf('/') + 1);
            f = new File(root, fileName);
          } else {
            f = root;
          }
        }
        if (f == null || !f.exists()) {
          Util.logerr("unable to resolve: " + esid);
          return null;
        }
        esid = f.getPath();
        return new XMLInputSource(null, esid, null);
      }
    };
    ArrayList<File> files = new ArrayList<>();
    for (File root : modelRoots) {
      ContainerUtil.addAll(files, root.listFiles());
    }
    loader.loadModel(model, files, resolver);
    Util.log(model.jtMap.size() + " java types loaded");
  }

}
