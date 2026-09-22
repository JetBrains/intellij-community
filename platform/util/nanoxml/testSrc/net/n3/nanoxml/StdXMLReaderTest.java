// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package net.n3.nanoxml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for how {@link StdXMLReader} resolves system IDs. A reader never resolves the external subset of a
 * {@code <!DOCTYPE>} declaration, and an override that wants one opens its own copy.
 * <p>
 * The tests that call the deprecated {@code StdXMLReader(String, String)} constructor go away with it. They are
 * marked below.
 */
public class StdXMLReaderTest {
  private static final String DOCUMENT = "<?xml version=\"1.0\"?><greeting who=\"world\"><nested/></greeting>";

  // --- the deprecated constructor loads a document itself; this group goes away with it ---

  @Test
  public void documentIsLoadedFromFileUrl(@TempDir Path dir) throws Exception {
    Path xml = writeDocument(dir, DOCUMENT);
    assertEquals("greeting", parseRootTag(openDocument(xml.toUri().toString())));
  }

  @Test
  public void documentIsLoadedFromPlainPath(@TempDir Path dir) throws Exception {
    Path xml = writeDocument(dir, DOCUMENT);
    assertEquals("greeting", parseRootTag(openDocument(xml.toString())));
  }

  @Test
  public void documentIsLoadedFromJarEntry(@TempDir Path dir) throws Exception {
    Path jar = dir.resolve("docs.jar");
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      out.putNextEntry(new JarEntry("inside.xml"));
      out.write("<?xml version=\"1.0\"?><injar/>".getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    assertEquals("injar", parseRootTag(openDocument("jar:" + jar.toUri() + "!/inside.xml")));
  }

  @Test
  public void missingLocalDocumentIsReported(@TempDir Path dir) {
    String systemID = dir.resolve("absent.xml").toUri().toString();
    assertThrows(FileNotFoundException.class, () -> openDocument(systemID));
  }

  // --- the external subset of a <!DOCTYPE>, which is never resolved ---

  @Test
  public void externalDoctypeIsNotResolved() throws Exception {
    String withDoctype = "<?xml version=\"1.0\"?>" +
                         "<!DOCTYPE greeting SYSTEM \"https://127.0.0.1:1/greeting.dtd\">" +
                         DOCUMENT;
    assertEquals("greeting", parseRootTag(new StdXMLReader(new StringReader(withDoctype))));
  }

  @Test
  public void relativeDoctypeOfLocalDocumentIsNotOpened(@TempDir Path dir) throws Exception {
    // a document loaded from a file resolves a relative system id against that file, so the DTD below is a path
    // next to the document. It does not exist on purpose: if the reader opened it, the parse would fail.
    Path xml = writeDocument(dir, "<?xml version=\"1.0\"?><!DOCTYPE greeting SYSTEM \"greeting.dtd\">" + DOCUMENT);
    assertEquals("greeting", parseRootTag(openDocument(xml.toUri().toString())));
  }

  @Test
  public void doctypeWithoutExternalIdOrInternalSubset() throws Exception {
    // JAXP hit a NPE on exactly this shape when told to skip DTDs, see JDK-8329295
    assertEquals("greeting", parseRootTag(new StdXMLReader(new StringReader("<!DOCTYPE greeting>" + DOCUMENT))));
  }

  @Test
  public void doctypeWithInternalSubsetOnly() throws Exception {
    String withInternalSubset = "<!DOCTYPE greeting [<!ENTITY who \"internal\">]>" + DOCUMENT;
    assertEquals("greeting", parseRootTag(new StdXMLReader(new StringReader(withInternalSubset))));
  }

  @Test
  public void resolutionCanBeOptedInForABundledCopy(@TempDir Path dir) throws Exception {
    Path bundledCopy = dir.resolve("greeting.dtd");
    Files.writeString(bundledCopy, "<!ENTITY who \"resolved\">");
    URL bundled = bundledCopy.toUri().toURL();

    StdXMLReader reader = new StdXMLReader(new StringReader(DOCUMENT)) {
      @Override
      public Reader openStream(String publicID, String systemID) throws IOException {
        if (!"https://example.com/greeting.dtd".equals(systemID)) return super.openStream(publicID, systemID);
        return new InputStreamReader(bundled.openStream(), StandardCharsets.UTF_8);
      }
    };

    assertEquals("<!ENTITY who \"resolved\">", readFully(reader.openStream(null, "https://example.com/greeting.dtd")));
    // an ID the override does not recognize still resolves to nothing
    assertEquals(" ", readFully(reader.openStream(null, "https://example.com/other.dtd")));
  }

  @SuppressWarnings("removal")
  private static StdXMLReader openDocument(String systemID) throws IOException {
    return new StdXMLReader(null, systemID);
  }

  private static String readFully(Reader reader) throws Exception {
    StringBuilder text = new StringBuilder();
    int ch;
    while ((ch = reader.read()) >= 0) {
      text.append((char)ch);
    }
    return text.toString();
  }

  private static Path writeDocument(Path dir, String text) throws Exception {
    Path xml = dir.resolve("doc.xml");
    Files.writeString(xml, text);
    return xml;
  }

  private static String parseRootTag(StdXMLReader reader) throws Exception {
    RootTagBuilder builder = new RootTagBuilder();
    new StdXMLParser(reader, builder, new EmptyValidator(), new EmptyEntityResolver()).parse();
    return builder.rootTag;
  }

  private static final class RootTagBuilder implements IXMLBuilder {
    private String rootTag;

    @Override
    public void startBuilding(String systemID, int lineNr) { }

    @Override
    public void newProcessingInstruction(String target, Reader reader) { }

    @Override
    public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) {
      if (rootTag == null) {
        rootTag = name;
      }
    }

    @Override
    public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) { }

    @Override
    public void elementAttributesProcessed(String name, String nsPrefix, String nsURI) { }

    @Override
    public void endElement(String name, String nsPrefix, String nsURI) { }

    @Override
    public void addPCData(Reader reader, String systemID, int lineNr) { }

    @Override
    public Object getResult() {
      return rootTag;
    }
  }

  private static final class EmptyValidator extends NonValidator {
    @Override
    public void elementStarted(String name, String systemId, int lineNr) { }

    @Override
    public void attributeAdded(String key, String value, String systemId, int lineNr) { }

    @Override
    public void elementAttributesProcessed(String name, Properties extraAttributes, String systemId, int lineNr) { }
  }

  private static final class EmptyEntityResolver implements IXMLEntityResolver {
    @Override
    public void addInternalEntity(String name, String value) { }

    @Override
    public void addExternalEntity(String name, String publicID, String systemID) { }

    @Override
    public Reader getEntity(StdXMLReader xmlReader, String name) {
      return new StringReader("");
    }

    @Override
    public boolean isExternalEntity(String name) {
      return false;
    }
  }
}
