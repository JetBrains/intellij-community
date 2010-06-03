/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.io.URLUtil;
import com.intellij.util.io.fs.IFile;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jdom.input.DOMBuilder;
import org.jdom.input.SAXBuilder;
import org.jdom.output.DOMOutputter;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMUtil {
  private static final ThreadLocal<SAXBuilder> ourSaxBuilder = new ThreadLocal<SAXBuilder>(){
    protected SAXBuilder initialValue() {
      SAXBuilder saxBuilder = new SAXBuilder();
      saxBuilder.setEntityResolver(new EntityResolver() {
        public InputSource resolveEntity(String publicId, String systemId) {
          return new InputSource(new CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY));
        }
      });
      return saxBuilder;
    }
  };

  private static class LoggerHolder {
    private static final Logger ourLogger = Logger.getInstance("#com.intellij.openapi.util.JDOMUtil");
  }

  private static Logger getLogger() {
    return LoggerHolder.ourLogger;
  }

  private static final String ENCODING = "UTF-8";

  public static boolean areElementsEqual(Element e1, Element e2) {
    if (e1 == null && e2 == null) return true;
    if (e1 == null || e2 == null) return false;

    return Comparing.equal(e1.getName(), e2.getName())
           && attListsEqual(e1.getAttributes(), e2.getAttributes())
           && contentListsEqual(e1.getContent(CONTENT_FILTER), e2.getContent(CONTENT_FILTER));
  }

  private static final EmptyTextFilter CONTENT_FILTER = new EmptyTextFilter();

  public static int getTreeHash(final Element root) {
    return addToHash(0, root);
  }

  public static int getTreeHash(Document document) {
    return getTreeHash(document.getRootElement());
  }

  private static int addToHash(int i, final Element element) {
    i = addToHash(i, element.getName());
    i = addToHash(i, element.getText());

    final List list = element.getAttributes();
    for (Object aList : list) {
      Attribute attribute = (Attribute)aList;
      i = addToHash(i, attribute);
    }

    //noinspection unchecked
    final List<Element> children = element.getChildren();
    for (Element child : children) { //iterator is used here which is more efficient than get(index)
      i = addToHash(i, child);
    }

    return i;
  }

  private static int addToHash(int i, final Attribute attribute) {
    i = addToHash(i, attribute.getName());
    i = addToHash(i, attribute.getValue());
    return i;
  }

  private static int addToHash(final int i, final String s) {
    return i * 31 + s.hashCode();
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static Object[] getChildNodesWithAttrs(final Element e) {
    ArrayList<Object> result = new ArrayList<Object>();
    result.addAll(e.getContent());
    result.addAll(e.getAttributes());
    return ArrayUtil.toObjectArray(result);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static Content[] getContent(final Element m) {
    final List list = m.getContent();
    return (Content[])list.toArray(new Content[list.size()]);
  }

  @SuppressWarnings({"unchecked"})
  @NotNull
  public static Element[] getElements(final Element m) {
    final List list = m.getChildren();
    return (Element[])list.toArray(new Element[list.size()]);
  }

  @NotNull
  public static String concatTextNodesValues(final Object[] nodes) {
    StringBuilder result = new StringBuilder();
    for (Object node : nodes) {
      result.append(((Content)node).getValue());
    }
    return result.toString();
  }

  public static void addContent(final Element targetElement, final Object node) {
    if (node instanceof Content) {
      Content content = (Content)node;
      targetElement.addContent(content);
    }
    else if (node instanceof List) {
      List list = (List)node;
      targetElement.addContent(list);
    }
    else {
      throw new IllegalArgumentException("Wrong node: " + node);
    }
  }

  public static void internElement(final Element element, final StringInterner interner) {
    element.setName(intern(interner, element.getName()));

    final List attributes = element.getAttributes();
    for (Object o : attributes) {
      Attribute attr = (Attribute)o;
      attr.setName(intern(interner, attr.getName()));
      attr.setValue(intern(interner, attr.getValue()));
    }

    final List content = element.getContent();
    for (Object o : content) {
      if (o instanceof Element) {
        Element e = (Element)o;
        internElement(e, interner);
      }
      else if (o instanceof Text) {
        Text text = (Text)o;
        text.setText(intern(interner, text.getText()));
      }
      else if (o instanceof Comment) {
        Comment comment = (Comment)o;
        comment.setText(intern(interner, comment.getText()));
      }
      else {
        throw new IllegalArgumentException("Wrong node: " + o);
      }
    }
  }

  @NotNull
  private static String intern(final StringInterner interner, final String s) {
    synchronized (interner) {
      return interner.intern(s);
    }
  }

  @NotNull
  public static String legalizeText(@NotNull final String str) {
    StringReader reader = new StringReader(str);
    StringBuilder result = new StringBuilder();

    while(true) {
      //noinspection EmptyCatchBlock
      try {
        int each = reader.read();
        if (each == -1) break;

        if (Verifier.isXMLCharacter(each)) {
          result.append((char)each);
        } else {
          result.append("0x").append(Long.toHexString(each).toUpperCase());
        }
      }
      catch (IOException e) {
      }
    }

    return result.toString().replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  }


  private static class EmptyTextFilter implements Filter {
    public boolean matches(Object obj) {
      if (obj instanceof Text) {
        final Text t = (Text)obj;
        return !CharArrayUtil.containsOnlyWhiteSpaces(t.getText());
      }
      return true;
    }
  }

  private static boolean contentListsEqual(final List c1, final List c2) {
    if (c1 == null && c2 == null) return true;
    if (c1 == null || c2 == null) return false;

    Iterator l1 = c1.listIterator();
    Iterator l2 = c2.listIterator();
    while (l1.hasNext() && l2.hasNext()) {
      if (!contentsEqual((Content)l1.next(), (Content)l2.next())) {
        return false;
      }
    }

    return l1.hasNext() == l2.hasNext();
  }

  private static boolean contentsEqual(Content c1, Content c2) {
    if (!(c1 instanceof Element) && !(c2 instanceof Element)) {
      return c1.getValue().equals(c2.getValue());
    }

    return c1 instanceof Element && c2 instanceof Element && areElementsEqual((Element)c1, (Element)c2);

  }

  private static boolean attListsEqual(List a1, List a2) {
    if (a1.size() != a2.size()) return false;
    for (int i = 0; i < a1.size(); i++) {
      if (!attEqual((Attribute)a1.get(i), (Attribute)a2.get(i))) return false;
    }
    return true;
  }

  private static boolean attEqual(Attribute a1, Attribute a2) {
    return a1.getName().equals(a2.getName()) && a1.getValue().equals(a2.getValue());
  }

  public static boolean areDocumentsEqual(Document d1, Document d2) {
    if(d1.hasRootElement() != d2.hasRootElement()) return false;

    if(!d1.hasRootElement()) return true;

    CharArrayWriter w1 = new CharArrayWriter();
    CharArrayWriter w2 = new CharArrayWriter();

    try {
      writeDocument(d1, w1, "\n");
      writeDocument(d2, w2, "\n");
    }
    catch (IOException e) {
      getLogger().error(e);
    }

    return w1.size() == w2.size() && w1.toString().equals(w2.toString());

  }

  @NotNull
  public static Document loadDocument(char[] chars, int length) throws IOException, JDOMException {
    SAXBuilder builder = ourSaxBuilder.get();
    return builder.build(new CharArrayReader(chars, 0, length));
  }

  @NotNull
  public static Document loadDocument(CharSequence seq) throws IOException, JDOMException {
    SAXBuilder builder = ourSaxBuilder.get();
    return builder.build(new CharSequenceReader(seq));
  }

  @NotNull
  public static Document loadDocument(File file) throws JDOMException, IOException {
    final BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
    try {
      return loadDocument(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  public static Document loadDocument(final IFile iFile) throws IOException, JDOMException {
    final BufferedInputStream inputStream = new BufferedInputStream(iFile.openInputStream());
    try {
      return loadDocument(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  @NotNull
  public static Document loadDocument(@NotNull InputStream stream) throws JDOMException, IOException {
    SAXBuilder saxBuilder = ourSaxBuilder.get();
    InputStreamReader reader = new InputStreamReader(stream, ENCODING);
    try {
      return saxBuilder.build(reader);
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static Document loadDocument(URL url) throws JDOMException, IOException {
    return loadDocument(URLUtil.openStream(url));
  }

  @NotNull
  public static Document loadResourceDocument(URL url) throws JDOMException, IOException {
    return loadDocument(URLUtil.openResourceStream(url));
  }

  public static void writeDocument(Document document, String filePath, String lineSeparator) throws IOException {
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(filePath));
    try {
      writeDocument(document, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  public static void writeDocument(Document document, File file, String lineSeparator) throws IOException {
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
    try {
      writeDocument(document, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  public static void writeDocument(Document document, OutputStream stream, String lineSeparator) throws IOException {
    writeDocument(document, new OutputStreamWriter(stream, ENCODING), lineSeparator);
  }


  @NotNull
  public static byte[] printDocument(Document document, String lineSeparator) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();
    writeDocument(document, writer, lineSeparator);

    return new String(writer.toCharArray()).getBytes(ENCODING);
  }

  @NotNull
  public static String writeDocument(Document document, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeDocument(document, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException e) {
      // Can't be
      return "";
    }
  }

  @NotNull
  public static String writeParent(Parent element, String lineSeparator) throws IOException {
    try {
      final StringWriter writer = new StringWriter();
      writeParent(element, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException ignored) {
      throw new RuntimeException(ignored);
    }
  }

  public static void writeParent(Parent element, Writer writer, String lineSeparator) throws IOException {
    if (element instanceof Element) {
      writeElement((Element) element, writer, lineSeparator);
    } else if (element instanceof Document) {
      writeDocument((Document) element, writer, lineSeparator);
    }
  }

  public static void writeElement(Element element, Writer writer, String lineSeparator) throws IOException {
    XMLOutputter xmlOutputter = createOutputter(lineSeparator);
    try {
      xmlOutputter.output(element, writer);
    }
    catch (NullPointerException ex) {
      getLogger().error(ex);
      printDiagnostics(element, "");
    }
  }

  @NotNull
  public static String writeElement(Element element, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeElement(element, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException ignored) {
      throw new RuntimeException(ignored);
    }
  }

  public static void writeDocument(Document document, Writer writer, String lineSeparator) throws IOException {
    XMLOutputter xmlOutputter = createOutputter(lineSeparator);
    try {
      xmlOutputter.output(document, writer);
    }
    catch (NullPointerException ex) {
      getLogger().error(ex);
      printDiagnostics(document.getRootElement(), "");
    }
  }

  @NotNull
  public static XMLOutputter createOutputter(String lineSeparator) {
    XMLOutputter xmlOutputter = new MyXMLOutputter();
    Format format = Format.getCompactFormat().
      setIndent("  ").
      setTextMode(Format.TextMode.NORMALIZE).
      setEncoding(ENCODING).
      setOmitEncoding(false).
      setOmitDeclaration(false).
      setLineSeparator(lineSeparator);
    xmlOutputter.setFormat(format);
    return xmlOutputter;
  }

  /**
   * Returns null if no escapement necessary.
   */
  @Nullable
  private static String escapeChar(char c, boolean escapeLineEnds) {
    switch (c) {
      case '\n': return escapeLineEnds ? "&#10;" : null;
      case '\r': return escapeLineEnds ? "&#13;" : null;
      case '\t': return escapeLineEnds ? "&#9;" : null;
      case '<':  return "&lt;";
      case '>':  return "&gt;";
      case '\"': return "&quot;";
      case '&':  return "&amp;";
    }
    return null;
  }

  @NotNull
  public static String escapeText(String text) {
    return escapeText(text, false);
  }

  @NotNull
  private static String escapeText(String text, boolean escapeLineEnds) {
    StringBuffer buffer = null;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      final String quotation = escapeChar(ch, escapeLineEnds);

      if (buffer == null) {
        if (quotation != null) {
          // An quotation occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuffer(text.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(text.substring(0, i));
          buffer.append(quotation);
        }
      }
      else {
        if (quotation == null) {
          buffer.append(ch);
        }
        else {
          buffer.append(quotation);
        }
      }
    }
    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? text : buffer.toString();
  }

  @NotNull
  public static List<Element> getChildrenFromAllNamespaces(final Element element, @NonNls final String name) {
    final ArrayList<Element> result = new ArrayList<Element>();
    final List children = element.getChildren();
    for (final Object aChildren : children) {
      Element child = (Element)aChildren;
      if (name.equals(child.getName())) {
        result.add(child);
      }
    }
    return result;
  }

  public static class MyXMLOutputter extends XMLOutputter {
    public String escapeAttributeEntities(String str) {
      return escapeText(str, true);
    }

    public String escapeElementEntities(String str) {
      return escapeText(str, false);
    }
  }

  private static void printDiagnostics(Element element, String prefix) {
    ElementInfo info = getElementInfo(element);
    prefix = prefix + "/" + info.name;
    if (info.hasNullAttributes) {
      System.err.println(prefix);
    }

    //noinspection unchecked
    List<Element> children = element.getChildren();
    for (final Element child : children) {
      printDiagnostics(child, prefix);
    }
  }

  @NotNull
  private static ElementInfo getElementInfo(Element element) {
    ElementInfo info = new ElementInfo();
    StringBuilder buf = new StringBuilder(element.getName());
    List attributes = element.getAttributes();
    if (attributes != null) {
      int length = attributes.size();
      if (length > 0) {
        buf.append("[");
        for (int idx = 0; idx < length; idx++) {
          Attribute attr = (Attribute)attributes.get(idx);
          if (idx != 0) {
            buf.append(";");
          }
          buf.append(attr.getName());
          buf.append("=");
          buf.append(attr.getValue());
          if (attr.getValue() == null) {
            info.hasNullAttributes = true;
          }
        }
        buf.append("]");
      }
    }
    info.name = buf.toString();
    return info;
  }

  public static void updateFileSet(File[] oldFiles, String[] newFilePaths, Document[] newFileDocuments, String lineSeparator)
    throws IOException {
    getLogger().assertTrue(newFilePaths.length == newFileDocuments.length);

    ArrayList<String> writtenFilesPaths = new ArrayList<String>();

    // check if files are writable
    for (String newFilePath : newFilePaths) {
      File file = new File(newFilePath);
      if (file.exists() && !file.canWrite()) {
        throw new IOException("File \"" + newFilePath + "\" is not writeable");
      }
    }
    for (File file : oldFiles) {
      if (file.exists() && !file.canWrite()) {
        throw new IOException("File \"" + file.getAbsolutePath() + "\" is not writeable");
      }
    }

    for (int i = 0; i < newFilePaths.length; i++) {
      String newFilePath = newFilePaths[i];

      writeDocument(newFileDocuments[i], newFilePath, lineSeparator);
      writtenFilesPaths.add(newFilePath);
    }

    // delete files if necessary

    outer:
    for (File oldFile : oldFiles) {
      String oldFilePath = oldFile.getAbsolutePath();
      for (final String writtenFilesPath : writtenFilesPaths) {
        if (oldFilePath.equals(writtenFilesPath)) {
          continue outer;
        }
      }
      boolean result = oldFile.delete();
      if (!result) {
        throw new IOException("File \"" + oldFilePath + "\" was not deleted");
      }
    }
  }

  private static class ElementInfo {
    public String name = "";
    public boolean hasNullAttributes = false;
  }

  public static org.w3c.dom.Element convertToDOM(@NotNull Element e) {
    try {
      final Document d = new Document();
      final Element newRoot = new Element(e.getName());
      final List attributes = e.getAttributes();

      for (Object o : attributes) {
        Attribute attr = (Attribute)o;
        newRoot.setAttribute(attr.getName(), attr.getValue(), attr.getNamespace());
      }

      d.addContent(newRoot);
      newRoot.addContent(e.cloneContent());

      return new DOMOutputter().output(d).getDocumentElement();
    }
    catch (JDOMException e1) {
      throw new RuntimeException(e1);
    }
  }

  public static Element convertFromDOM(org.w3c.dom.Element e) {
    return new DOMBuilder().build(e);
  }

  public static String getValue(Object node) {
    if (node instanceof Content) {
      Content content = (Content)node;
      return content.getValue();
    }
    else if (node instanceof Attribute) {
      Attribute attribute = (Attribute)node;
      return attribute.getValue();
    }
    else {
      throw new IllegalArgumentException("Wrong node: " + node);
    }
  }
}
