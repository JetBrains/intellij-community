// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import org.jetbrains.annotations.NonNls;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Can be used to update groovy.sdk.xml md5 values and does some basic validation.
 * The same file is available onine at
 * <a href="https://jetbrains.team/p/ld/repositories/frameworks-data/groovy/index.xml">https://framework.jetbrains.com/groovy/index.xml</a>
 *
 * @author Bas Leijdekkers
 */
final class GroovySdk {

  private static final @NonNls String groovySdkPath = "plugins/groovy/resources/org/jetbrains/plugins/groovy/config/groovy.sdk.xml";

  static void main() throws Exception {
    update(groovySdkPath);
    System.exit(0);

    String version = "6.0.0-alpha-1";
    // string extracted from apache-groovy-sdk-<version>.zip
    String jars = """
      groovy-6.0.0-alpha-1.jar
      groovy-ant-6.0.0-alpha-1.jar
      groovy-astbuilder-6.0.0-alpha-1.jar
      groovy-cli-commons-6.0.0-alpha-1.jar
      groovy-cli-picocli-6.0.0-alpha-1.jar
      groovy-console-6.0.0-alpha-1.jar
      groovy-contracts-6.0.0-alpha-1.jar
      groovy-csv-6.0.0-alpha-1.jar
      groovy-datetime-6.0.0-alpha-1.jar
      groovy-dateutil-6.0.0-alpha-1.jar
      groovy-docgenerator-6.0.0-alpha-1.jar
      groovy-ginq-6.0.0-alpha-1.jar
      groovy-grape-ivy-6.0.0-alpha-1.jar
      groovy-grape-maven-6.0.0-alpha-1.jar
      groovy-groovydoc-6.0.0-alpha-1.jar
      groovy-groovysh-6.0.0-alpha-1.jar
      groovy-http-builder-6.0.0-alpha-1.jar
      groovy-jmx-6.0.0-alpha-1.jar
      groovy-json-6.0.0-alpha-1.jar
      groovy-jsr223-6.0.0-alpha-1.jar
      groovy-macro-6.0.0-alpha-1.jar
      groovy-macro-library-6.0.0-alpha-1.jar
      groovy-markdown-6.0.0-alpha-1.jar
      groovy-nio-6.0.0-alpha-1.jar
      groovy-reactor-6.0.0-alpha-1.jar
      groovy-rxjava-6.0.0-alpha-1.jar
      groovy-servlet-6.0.0-alpha-1.jar
      groovy-sql-6.0.0-alpha-1.jar
      groovy-swing-6.0.0-alpha-1.jar
      groovy-templates-6.0.0-alpha-1.jar
      groovy-test-6.0.0-alpha-1.jar
      groovy-test-junit5-6.0.0-alpha-1.jar
      groovy-test-junit6-6.0.0-alpha-1.jar
      groovy-testng-6.0.0-alpha-1.jar
      groovy-toml-6.0.0-alpha-1.jar
      groovy-typecheckers-6.0.0-alpha-1.jar
      groovy-xml-6.0.0-alpha-1.jar
      groovy-yaml-6.0.0-alpha-1.jar""";
    createArtifact(jars, version);
  }

  private static void createArtifact(String jars, String version) throws URISyntaxException, IOException {
    StringBuilder out = new StringBuilder();
    for (String jar : jars.split("\n")) {
      int index = jar.indexOf("-" + version);
      if (index == -1) throw new AssertionError("incorrect version specified: " + version);
      String name = jar.substring(0, index);
      String url = "https://repo1.maven.org/maven2/org/apache/groovy/" + name + "/" + version + "/" + jar;
      String srcUrl = "https://repo1.maven.org/maven2/org/apache/groovy/" + name + "/" + version + "/" + name + "-" + version + "-sources.jar";
      out.append("    <item url=\"").append(url).append("\"\n          srcUrl=\"").append(srcUrl);
      try (Reader reader = new InputStreamReader(new URI(url + ".md5").toURL().openStream(), StandardCharsets.UTF_8)) {
        String md5 = reader.readAllAsString();
        out.append("\"\n          md5=\"").append(md5).append("\"/>\n");
      }
    }
    System.out.println(out);
  }

  private static void update(@NonNls String path) throws ParserConfigurationException, SAXException, IOException, URISyntaxException {
    Document document = readDocument(path);
    NodeList artifacts = document.getDocumentElement().getElementsByTagName("artifact");
    for (int i = 0; i < artifacts.getLength(); i++) {
      Element artifact = (Element)artifacts.item(i);
      //String version = artifact.getAttribute("version");
      NodeList items = artifact.getElementsByTagName("item");
      for (int j = 0; j < items.getLength(); j++) {
        NamedNodeMap attributes = items.item(j).getAttributes();
        String srcUrl = attributes.getNamedItem("srcUrl").getNodeValue();
        int index = srcUrl.lastIndexOf("-sources");
        String url = attributes.getNamedItem("url").getNodeValue();
        if (!url.equals(srcUrl.substring(0, index) + srcUrl.substring(index + 8))) {
          System.out.println("Incorrect srcUrl: " + srcUrl);
        }
        Node md5 = attributes.getNamedItem("md5");
        try (Reader reader = new InputStreamReader(new URI(url + ".md5").toURL().openStream(), StandardCharsets.UTF_8)) {
          String expectedMD5 = reader.readAllAsString();
          String md5Value = md5.getNodeValue();
          if (!expectedMD5.equals(md5Value)) {
            System.out.println("Incorrect md5: " + md5Value + " expected md5: " + expectedMD5);
            md5.setNodeValue(expectedMD5);
          }
        }
      }
    }
    writeDocument(document);
  }

  private static Document readDocument(@NonNls String path) throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    File sdkFile = new File(path);
    if (!sdkFile.exists()) {
      sdkFile = new File("community/" + path);
      assert sdkFile.exists() : "groovy.sdk.xml not found";
    }
    return factory.newDocumentBuilder().parse(sdkFile);
  }

  private static void writeDocument(Document doc) {
    StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    Element element = doc.getDocumentElement();
    builder.append("<").append(element.getNodeName()).append(">\n");
    Node child = element.getFirstChild();
    while (child != null) {
      if (child instanceof Element childElement) {
        builder.append("  <").append(child.getNodeName());
        builder.append(" ");
        builder.append(childElement.getAttributeNode("name"));
        builder.append(" ");
        builder.append(childElement.getAttributeNode("version"));
        builder.append(">\n");
        Node grandChild = childElement.getFirstChild();
        while (grandChild != null) {
          if (grandChild instanceof Element grandChildElement) {
            builder.append("    <").append(grandChild.getNodeName());
            builder.append(" ").append(grandChildElement.getAttributeNode("url"));
            builder.append("\n          ").append(grandChildElement.getAttributeNode("srcUrl"));
            builder.append("\n          ").append(grandChildElement.getAttributeNode("md5"));
            builder.append("/>\n");
          }
          grandChild = grandChild.getNextSibling();
        }
        builder.append("  </").append(child.getNodeName()).append(">\n");
      }
      child = child.getNextSibling();
    }
    builder.append("</").append(element.getNodeName()).append(">\n");
    System.out.println(builder);
  }
}
