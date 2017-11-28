/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.StringInterner;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.text.StringFactory;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jdom.input.SAXBuilder;
import org.jdom.input.SAXHandler;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import java.io.*;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author mike
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class JDOMUtil {
  private static final ThreadLocal<SoftReference<SAXBuilder>> ourSaxBuilder = new ThreadLocal<SoftReference<SAXBuilder>>();
  public static final Condition<Attribute> NOT_EMPTY_VALUE_CONDITION = new Condition<Attribute>() {
    @Override
    public boolean value(Attribute attribute) {
      return !StringUtil.isEmpty(attribute.getValue());
    }
  };

  private JDOMUtil() { }

  @NotNull
  public static List<Element> getChildren(@Nullable Element parent) {
    if (parent == null) {
      return Collections.emptyList();
    }
    else {
      return parent.getChildren();
    }
  }

  @NotNull
  public static List<Element> getChildren(@Nullable Element parent, @NotNull String name) {
    if (parent != null) {
      return parent.getChildren(name);
    }
    return Collections.emptyList();
  }

  @SuppressWarnings("UtilityClassWithoutPrivateConstructor")
  private static class LoggerHolder {
    private static final Logger ourLogger = Logger.getInstance("#com.intellij.openapi.util.JDOMUtil");
  }

  private static Logger getLogger() {
    return LoggerHolder.ourLogger;
  }

  public static boolean areElementsEqual(@Nullable Element e1, @Nullable Element e2) {
    return areElementsEqual(e1, e2, false);
  }

  /**
   *
   * @param ignoreEmptyAttrValues defines if elements like <element foo="bar" skip_it=""/> and <element foo="bar"/> are 'equal'
   * @return {@code true} if two elements are deep-equals by their content and attributes
   */
  public static boolean areElementsEqual(@Nullable Element e1, @Nullable Element e2, boolean ignoreEmptyAttrValues) {
    if (e1 == null && e2 == null) return true;
    if (e1 == null || e2 == null) return false;

    return Comparing.equal(e1.getName(), e2.getName())
           && attListsEqual(e1.getAttributes(), e2.getAttributes(), ignoreEmptyAttrValues)
           && contentListsEqual(e1.getContent(CONTENT_FILTER), e2.getContent(CONTENT_FILTER), ignoreEmptyAttrValues);
  }

  private static final EmptyTextFilter CONTENT_FILTER = new EmptyTextFilter();

  public static int getTreeHash(@NotNull Element root) {
    return addToHash(0, root, true);
  }

  private static int addToHash(int i, @NotNull Element element, boolean skipEmptyText) {
    i = addToHash(i, element.getName());

    for (Attribute attribute : element.getAttributes()) {
      i = addToHash(i, attribute.getName());
      i = addToHash(i, attribute.getValue());
    }

    for (Content child : element.getContent()) {
      if (child instanceof Element) {
        i = addToHash(i, (Element)child, skipEmptyText);
      }
      else if (child instanceof Text) {
        String text = ((Text)child).getText();
        if (!skipEmptyText || !StringUtil.isEmptyOrSpaces(text)) {
          i = addToHash(i, text);
        }
      }
    }
    return i;
  }

  private static int addToHash(int i, @NotNull String s) {
    return i * 31 + s.hashCode();
  }

  /**
   * @deprecated Use Element.getChildren() directly
   */
  @NotNull
  @Deprecated
  public static Element[] getElements(@NotNull Element m) {
    List<Element> list = m.getChildren();
    return list.toArray(new Element[list.size()]);
  }

  public static void internElement(@NotNull Element element, @NotNull StringInterner interner) {
    element.setName(interner.intern(element.getName()));

    for (Attribute attr : element.getAttributes()) {
      attr.setName(interner.intern(attr.getName()));
      attr.setValue(interner.intern(attr.getValue()));
    }

    for (Content o : element.getContent()) {
      if (o instanceof Element) {
        internElement((Element)o, interner);
      }
      else if (o instanceof Text) {
        ((Text)o).setText(interner.intern(o.getValue()));
      }
    }
  }

  @NotNull
  public static String legalizeText(@NotNull String str) {
    return legalizeChars(str).toString();
  }

  @NotNull
  public static CharSequence legalizeChars(@NotNull CharSequence str) {
    StringBuilder result = new StringBuilder(str.length());
    for (int i = 0, len = str.length(); i < len; i ++) {
      appendLegalized(result, str.charAt(i));
    }
    return result;
  }

  private static void appendLegalized(@NotNull StringBuilder sb, char each) {
    if (each == '<' || each == '>') {
      sb.append(each == '<' ? "&lt;" : "&gt;");
    }
    else if (!Verifier.isXMLCharacter(each)) {
      sb.append("0x").append(StringUtil.toUpperCase(Long.toHexString(each)));
    }
    else {
      sb.append(each);
    }
  }

  private static class EmptyTextFilter implements Filter {
    @Override
    public boolean matches(Object obj) {
      return !(obj instanceof Text) || !CharArrayUtil.containsOnlyWhiteSpaces(((Text)obj).getText());
    }
  }

  private static boolean contentListsEqual(final List c1, final List c2, boolean ignoreEmptyAttrValues) {
    if (c1 == null && c2 == null) return true;
    if (c1 == null || c2 == null) return false;

    Iterator l1 = c1.listIterator();
    Iterator l2 = c2.listIterator();
    while (l1.hasNext() && l2.hasNext()) {
      if (!contentsEqual((Content)l1.next(), (Content)l2.next(), ignoreEmptyAttrValues)) {
        return false;
      }
    }

    return l1.hasNext() == l2.hasNext();
  }

  private static boolean contentsEqual(Content c1, Content c2, boolean ignoreEmptyAttrValues) {
    if (!(c1 instanceof Element) && !(c2 instanceof Element)) {
      return c1.getValue().equals(c2.getValue());
    }

    return c1 instanceof Element && c2 instanceof Element && areElementsEqual((Element)c1, (Element)c2, ignoreEmptyAttrValues);
  }

  private static boolean attListsEqual(@NotNull List<Attribute> l1, @NotNull List<Attribute> l2, boolean ignoreEmptyAttrValues) {
    if (ignoreEmptyAttrValues) {
      l1 = ContainerUtil.filter(l1, NOT_EMPTY_VALUE_CONDITION);
      l2 = ContainerUtil.filter(l2, NOT_EMPTY_VALUE_CONDITION);
    }
    if (l1.size() != l2.size()) return false;
    for (int i = 0; i < l1.size(); i++) {
      if (!attEqual(l1.get(i), l2.get(i))) return false;
    }
    return true;
  }

  private static boolean attEqual(@NotNull Attribute a1, @NotNull Attribute a2) {
    return a1.getName().equals(a2.getName()) && a1.getValue().equals(a2.getValue());
  }

  private static SAXBuilder getSaxBuilder() {
    SoftReference<SAXBuilder> reference = ourSaxBuilder.get();
    SAXBuilder saxBuilder = com.intellij.reference.SoftReference.dereference(reference);
    if (saxBuilder == null) {
      saxBuilder = new SAXBuilder() {
        @Override
        protected void configureParser(XMLReader parser, SAXHandler contentHandler) throws JDOMException {
          super.configureParser(parser, contentHandler);
          try {
            parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
          }
          catch (Exception ignore) {
          }
        }
      };
      saxBuilder.setEntityResolver(new EntityResolver() {
        @Override
        @NotNull
        public InputSource resolveEntity(String publicId, String systemId) {
          return new InputSource(new CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY));
        }
      });
      ourSaxBuilder.set(new SoftReference<SAXBuilder>(saxBuilder));
    }
    return saxBuilder;
  }

  /**
   * @deprecated Use {@link #load(CharSequence)}
   *
   * Direct usage of element allows to get rid of {@link Document#getRootElement()} because only Element is required in mostly all cases.
   */
  @NotNull
  @Deprecated
  public static Document loadDocument(@NotNull CharSequence seq) throws IOException, JDOMException {
    return loadDocument(new CharSequenceReader(seq));
  }

  public static Element load(@NotNull CharSequence seq) throws IOException, JDOMException {
    return load(new CharSequenceReader(seq));
  }

  @NotNull
  private static Document loadDocument(@NotNull Reader reader) throws IOException, JDOMException {
    try {
      return getSaxBuilder().build(reader);
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  public static Document loadDocument(File file) throws JDOMException, IOException {
    return loadDocument(new BufferedInputStream(new FileInputStream(file)));
  }

  @NotNull
  public static Element load(@NotNull File file) throws JDOMException, IOException {
    return load(new BufferedInputStream(new FileInputStream(file)));
  }

  @NotNull
  public static Document loadDocument(@NotNull InputStream stream) throws JDOMException, IOException {
    return loadDocument(new InputStreamReader(stream, CharsetToolkit.UTF8_CHARSET));
  }

  public static Element load(Reader reader) throws JDOMException, IOException {
    return reader == null ? null : loadDocument(reader).detachRootElement();
  }

  /**
   * Consider to use `loadElement` (JdomKt.loadElement from java) due to more efficient whitespace handling (cannot be changed here due to backward compatibility).
   */
  @Contract("null -> null; !null -> !null")
  public static Element load(InputStream stream) throws JDOMException, IOException {
    return stream == null ? null : loadDocument(stream).detachRootElement();
  }

  @NotNull
  public static Document loadDocument(@NotNull Class clazz, String resource) throws JDOMException, IOException {
    InputStream stream = clazz.getResourceAsStream(resource);
    if (stream == null) {
      throw new FileNotFoundException(resource);
    }
    return loadDocument(stream);
  }

  @NotNull
  public static Document loadDocument(@NotNull URL url) throws JDOMException, IOException {
    return loadDocument(URLUtil.openStream(url));
  }

  @NotNull
  public static Document loadResourceDocument(URL url) throws JDOMException, IOException {
    return loadDocument(URLUtil.openResourceStream(url));
  }

  public static void writeDocument(@NotNull Document document, @NotNull String filePath, String lineSeparator) throws IOException {
    OutputStream stream = new BufferedOutputStream(new FileOutputStream(filePath));
    try {
      writeDocument(document, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  public static void writeDocument(@NotNull Document document, @NotNull File file, String lineSeparator) throws IOException {
    write(document, file, lineSeparator);
  }

  public static void write(@NotNull Parent element, @NotNull File file) throws IOException {
    write(element, file, "\n");
  }

  public static void write(@NotNull Parent element, @NotNull File file, @NotNull String lineSeparator) throws IOException {
    FileUtil.createParentDirs(file);

    OutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
    try {
      write(element, stream, lineSeparator);
    }
    finally {
      stream.close();
    }
  }

  public static void writeDocument(@NotNull Document document, @NotNull OutputStream stream, String lineSeparator) throws IOException {
    write(document, stream, lineSeparator);
  }

  public static void write(@NotNull Parent element, @NotNull OutputStream stream, @NotNull String lineSeparator) throws IOException {
    OutputStreamWriter writer = new OutputStreamWriter(stream, CharsetToolkit.UTF8_CHARSET);
    try {
      if (element instanceof Document) {
        writeDocument((Document)element, writer, lineSeparator);
      }
      else {
        writeElement((Element) element, writer, lineSeparator);
      }
    }
    finally {
      writer.close();
    }
  }

  /**
   * @deprecated Use {@link #writeDocument(Document, String)} or {@link #writeElement(Element)}}
   */
  @NotNull
  @Deprecated
  public static byte[] printDocument(@NotNull Document document, String lineSeparator) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();
    writeDocument(document, writer, lineSeparator);

    return StringFactory.createShared(writer.toCharArray()).getBytes(CharsetToolkit.UTF8_CHARSET);
  }

  @NotNull
  public static String writeDocument(@NotNull Document document, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeDocument(document, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException ignored) {
      // Can't be
      return "";
    }
  }

  @NotNull
  public static String write(Parent element, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      write(element, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void write(Parent element, Writer writer, String lineSeparator) throws IOException {
    if (element instanceof Element) {
      writeElement((Element) element, writer, lineSeparator);
    } else if (element instanceof Document) {
      writeDocument((Document) element, writer, lineSeparator);
    }
  }

  public static void writeElement(@NotNull Element element, Writer writer, String lineSeparator) throws IOException {
    writeElement(element, writer, createOutputter(lineSeparator));
  }

  public static void writeElement(@NotNull Element element, @NotNull Writer writer, @NotNull XMLOutputter xmlOutputter) throws IOException {
    try {
      xmlOutputter.output(element, writer);
    }
    catch (NullPointerException ex) {
      getLogger().error(ex);
      printDiagnostics(element, "");
    }
  }

  @NotNull
  public static String writeElement(@NotNull Element element) {
    return writeElement(element, "\n");
  }

  @NotNull
  public static String writeElement(@NotNull Element element, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeElement(element, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static String writeChildren(@NotNull final Element element, @NotNull final String lineSeparator) throws IOException {
    final StringWriter writer = new StringWriter();
    for (Element child : element.getChildren()) {
      writeElement(child, writer, lineSeparator);
      writer.append(lineSeparator);
    }
    return writer.toString();
  }

  public static void writeDocument(@NotNull Document document, @NotNull Writer writer, String lineSeparator) throws IOException {
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
    return createOutputter(lineSeparator, null);
  }

  @NotNull
  public static XMLOutputter createOutputter(String lineSeparator, @Nullable ElementOutputFilter elementOutputFilter) {
    XMLOutputter xmlOutputter = new MyXMLOutputter(elementOutputFilter);
    Format format = Format.getCompactFormat().
      setIndent("  ").
      setTextMode(Format.TextMode.TRIM).
      setEncoding(CharsetToolkit.UTF8).
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
  private static String escapeChar(char c, boolean escapeApostrophes, boolean escapeSpaces, boolean escapeLineEnds) {
    switch (c) {
      case '\n': return escapeLineEnds ? "&#10;" : null;
      case '\r': return escapeLineEnds ? "&#13;" : null;
      case '\t': return escapeLineEnds ? "&#9;" : null;
      case ' ' : return escapeSpaces  ? "&#20" : null;
      case '<':  return "&lt;";
      case '>':  return "&gt;";
      case '\"': return "&quot;";
      case '\'': return escapeApostrophes ? "&apos;": null;
      case '&':  return "&amp;";
    }
    return null;
  }

  @NotNull
  public static String escapeText(@NotNull String text) {
    return escapeText(text, false, false);
  }

  @NotNull
  public static String escapeText(@NotNull String text, boolean escapeSpaces, boolean escapeLineEnds) {
    return escapeText(text, false, escapeSpaces, escapeLineEnds);
  }

  @NotNull
  public static String escapeText(@NotNull String text, boolean escapeApostrophes, boolean escapeSpaces, boolean escapeLineEnds) {
    StringBuilder buffer = null;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      final String quotation = escapeChar(ch, escapeApostrophes, escapeSpaces, escapeLineEnds);
      if (buffer == null) {
        if (quotation != null) {
          // An quotation occurred, so we'll have to use StringBuffer
          // (allocate room for it plus a few more entities).
          buffer = new StringBuilder(text.length() + 20);
          // Copy previous skipped characters and fall through
          // to pickup current character
          buffer.append(text, 0, i);
          buffer.append(quotation);
        }
      }
      else if (quotation == null) {
        buffer.append(ch);
      }
      else {
        buffer.append(quotation);
      }
    }
    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? text : buffer.toString();
  }

  public static class MyXMLOutputter extends XMLOutputter {
    private final ElementOutputFilter myElementOutputFilter;

    public MyXMLOutputter(@Nullable ElementOutputFilter filter) {
      myElementOutputFilter = filter;
    }

    public MyXMLOutputter() {
      this(null);
    }

    @Override
    @NotNull
    public String escapeAttributeEntities(@NotNull String str) {
      return escapeText(str, false, true);
    }

    @Override
    @NotNull
    public String escapeElementEntities(@NotNull String str) {
      return escapeText(str, false, false);
    }

    @Override
    protected void printElement(Writer out, Element element, int level, NamespaceStack namespaces) throws IOException {
      if (myElementOutputFilter == null || myElementOutputFilter.accept(element, level)) {
        super.printElement(out, element, level, namespaces);
      }
    }
  }

  public interface ElementOutputFilter {
    boolean accept(@NotNull Element element, int level);
  }

  private static void printDiagnostics(@NotNull Element element, String prefix) {
    ElementInfo info = getElementInfo(element);
    prefix += "/" + info.name;
    if (info.hasNullAttributes) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println(prefix);
    }

    for (final Element child : element.getChildren()) {
      printDiagnostics(child, prefix);
    }
  }

  @NotNull
  private static ElementInfo getElementInfo(@NotNull Element element) {
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

  public static void updateFileSet(@NotNull File[] oldFiles, @NotNull String[] newFilePaths, @NotNull Document[] newFileDocuments, String lineSeparator)
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
    @NotNull public String name = "";
    public boolean hasNullAttributes = false;
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

  public static boolean isEmpty(@Nullable Element element) {
    return element == null || (element.getAttributes().isEmpty() && element.getContent().isEmpty());
  }

  public static boolean isEmpty(@Nullable Element element, int attributeCount) {
    return element == null || (element.getAttributes().size() == attributeCount && element.getContent().isEmpty());
  }

  public static void merge(@NotNull Element to, @NotNull Element from) {
    for (Iterator<Element> iterator = from.getChildren().iterator(); iterator.hasNext(); ) {
      Element configuration = iterator.next();
      iterator.remove();
      to.addContent(configuration);
    }
    for (Iterator<Attribute> iterator = from.getAttributes().iterator(); iterator.hasNext(); ) {
      Attribute attribute = iterator.next();
      iterator.remove();
      to.setAttribute(attribute);
    }
  }
}
