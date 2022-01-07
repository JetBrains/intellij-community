// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.URLUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.xml.util.XmlStringUtil;
import org.codehaus.stax2.XMLStreamReader2;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.*;

import javax.xml.stream.XMLStreamException;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

public final class JDOMUtil {
  private static final @NonNls String X = "x";
  private static final @NonNls String Y = "y";
  private static final @NonNls String WIDTH = "width";
  private static final @NonNls String HEIGHT = "height";

  //xpointer($1)
  public static final Pattern XPOINTER_PATTERN = Pattern.compile("xpointer\\((.*)\\)");
  public static final Namespace XINCLUDE_NAMESPACE = Namespace.getNamespace("xi", "http://www.w3.org/2001/XInclude");
  // /$1(/$2)?/*
  public static final Pattern CHILDREN_PATTERN = Pattern.compile("/([^/]*)(/[^/]*)?/\\*");

  private JDOMUtil() { }

  public static @NotNull List<Element> getChildren(@Nullable Element parent) {
    return parent == null ? Collections.emptyList() : parent.getChildren();
  }

  public static @NotNull List<Element> getChildren(@Nullable Element parent, @NotNull String name) {
    if (parent != null) {
      return parent.getChildren(name);
    }
    return Collections.emptyList();
  }

  private static final class LoggerHolder {
    private static final Logger LOG = Logger.getInstance(JDOMUtil.class);
  }

  private static Logger getLogger() {
    return LoggerHolder.LOG;
  }

  public static boolean areElementsEqual(@Nullable Element e1, @Nullable Element e2) {
    return areElementsEqual(e1, e2, false);
  }

  /**
   * @param ignoreEmptyAttrValues defines if elements like <element foo="bar" skip_it=""/> and <element foo="bar"/> are 'equal'
   * @return {@code true} if two elements are deep-equals by their content and attributes
   */
  public static boolean areElementsEqual(@Nullable Element e1, @Nullable Element e2, boolean ignoreEmptyAttrValues) {
    if (e1 == null && e2 == null) return true;
    if (e1 == null || e2 == null) return false;

    return Objects.equals(e1.getName(), e2.getName())
           && isAttributesEqual(getAttributes(e1), getAttributes(e2), ignoreEmptyAttrValues)
           && areElementContentsEqual(e1, e2, ignoreEmptyAttrValues);
  }

  /**
   * Returns hash code which is consistent with {@link #areElementsEqual(Element, Element, boolean)}
   */
  public static int hashCode(@Nullable Element e, boolean ignoreEmptyAttrValues) {
    if (e == null) return 0;
    int hashCode = e.getName().hashCode();
    for (Attribute attribute : getAttributes(e)) {
      String value = attribute.getValue();
      if (!ignoreEmptyAttrValues || (value != null && !value.isEmpty())) {
        hashCode = hashCode * 31 * 31 + attribute.getName().hashCode() * 31 + value.hashCode();
      }
    }
    for (Content content : e.getContent(CONTENT_FILTER)) {
      int contentHash = content instanceof Element ? hashCode((Element)content, ignoreEmptyAttrValues) : e.getValue().hashCode();
      hashCode = hashCode * 31 + contentHash;
    }
    return hashCode;
  }

  public static boolean areElementContentsEqual(@NotNull Element e1, @NotNull Element e2, boolean ignoreEmptyAttrValues) {
    return contentListsEqual(e1.getContent(CONTENT_FILTER), e2.getContent(CONTENT_FILTER), ignoreEmptyAttrValues);
  }

  private static final EmptyTextFilter CONTENT_FILTER = new EmptyTextFilter();

  /**
   * @deprecated Use {@link Element#getChildren} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static Element @NotNull [] getElements(@NotNull Element m) {
    List<Element> list = m.getChildren();
    return list.toArray(new Element[0]);
  }

  public static @NotNull String legalizeText(@NotNull String str) {
    return legalizeChars(str).toString();
  }

  public static @NotNull CharSequence legalizeChars(@NotNull CharSequence str) {
    StringBuilder result = new StringBuilder(str.length());
    for (int i = 0, len = str.length(); i < len; i++) {
      appendLegalized(result, str.charAt(i));
    }
    return result;
  }

  private static void appendLegalized(@NotNull @NonNls StringBuilder sb, char each) {
    if (each == '<' || each == '>') {
      sb.append(each == '<' ? "&lt;" : "&gt;");
    }
    else if (!Verifier.isXMLCharacter(each)) {
      sb.append("0x").append(Strings.toUpperCase(Long.toHexString(each)));
    }
    else {
      sb.append(each);
    }
  }

  private static final class EmptyTextFilter implements Filter<Content> {
    @Override
    public boolean matches(Object obj) {
      return !(obj instanceof Text) || !CharArrayUtil.containsOnlyWhiteSpaces(((Text)obj).getText());
    }
  }

  private static boolean contentListsEqual(List<Content> c1, List<Content> c2, boolean ignoreEmptyAttrValues) {
    if (c1 == null && c2 == null) return true;
    if (c1 == null || c2 == null) return false;

    Iterator<Content> l1 = c1.listIterator();
    Iterator<Content> l2 = c2.listIterator();
    while (l1.hasNext() && l2.hasNext()) {
      if (!contentsEqual(l1.next(), l2.next(), ignoreEmptyAttrValues)) {
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

  private static boolean isAttributesEqual(@NotNull List<? extends Attribute> l1,
                                           @NotNull List<? extends Attribute> l2,
                                           boolean ignoreEmptyAttrValues) {
    if (ignoreEmptyAttrValues) {
      l1 = getNotEmptyAttributes(l1);
      l2 = getNotEmptyAttributes(l2);
    }
    if (l1.size() != l2.size()) {
      return false;
    }
    for (int i = 0; i < l1.size(); i++) {
      if (!attributesEqual(l1.get(i), l2.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static @NotNull List<? extends Attribute> getNotEmptyAttributes(@NotNull List<? extends Attribute> list) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    }

    List<Attribute> result = null;
    for (Attribute attribute : list) {
      String s = attribute.getValue();
      if (s != null && !s.isEmpty()) {
        if (result == null) {
          result = new ArrayList<>(list.size());
        }
        result.add(attribute);
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private static boolean attributesEqual(@NotNull Attribute a1, @NotNull Attribute a2) {
    return a1.getName().equals(a2.getName()) && a1.getValue().equals(a2.getValue());
  }

  private static @NotNull Document loadDocumentUsingStaX(@NotNull Reader reader) throws JDOMException, IOException {
    try {
      XMLStreamReader2 xmlStreamReader = StaxFactory.createXmlStreamReader(reader);
      try {
        return SafeStAXStreamBuilder.buildDocument(xmlStreamReader);
      }
      finally {
        xmlStreamReader.close();
      }
    }
    catch (XMLStreamException e) {
      throw new JDOMException(e.getMessage(), e);
    }
    finally {
      reader.close();
    }
  }

  private static @NotNull Element loadUsingStaX(@NotNull InputStream stream, @Nullable SafeJdomFactory factory) throws JDOMException {
    try {
      XMLStreamReader2 xmlStreamReader = StaxFactory.createXmlStreamReader(stream);
      try {
        return SafeStAXStreamBuilder.build(xmlStreamReader, true, true, factory == null ? SafeStAXStreamBuilder.FACTORY : factory);
      }
      finally {
        xmlStreamReader.close();
      }
    }
    catch (XMLStreamException e) {
      throw new JDOMException(e.getMessage(), e);
    }
  }

  public static @NotNull Element load(@NotNull CharSequence seq) throws IOException, JDOMException {
    return load(new CharSequenceReader(seq));
  }

  /**
   * @deprecated Use {@link #load(CharSequence)}
   * <p>
   * Direct usage of element allows getting rid of {@link Document#getRootElement()} because only Element is required in mostly all cases.
   */
  @Deprecated
  public static @NotNull Document loadDocument(@NotNull Reader reader) throws IOException, JDOMException {
    return loadDocumentUsingStaX(reader);
  }

  public static @NotNull Document loadDocument(@NotNull File file) throws JDOMException, IOException {
    return loadDocumentUsingStaX(new InputStreamReader(CharsetToolkit.inputStreamSkippingBOM(new BufferedInputStream(new FileInputStream(file))), StandardCharsets.UTF_8));
  }

  public static @NotNull Element load(@NotNull File file) throws JDOMException, IOException {
    return loadUsingStaX(new FileInputStream(file), null);
  }

  public static @NotNull Element load(@NotNull Path file) throws JDOMException, IOException {
    try {
      return loadUsingStaX(Files.newInputStream(file), null);
    }
    catch (ClosedFileSystemException e) {
      throw new IOException("Cannot read file from closed file system: " + file, e);
    }
  }

  /**
   * Internal use only.
   */
  @ApiStatus.Internal
  public static @NotNull Element load(@NotNull File file, @Nullable SafeJdomFactory factory) throws JDOMException, IOException {
    return loadUsingStaX(new FileInputStream(file), factory);
  }

  @ApiStatus.Internal
  public static @NotNull Element load(@NotNull Path file, @Nullable SafeJdomFactory factory) throws JDOMException, IOException {
    try {
      return loadUsingStaX(Files.newInputStream(file), factory);
    }
    catch (ClosedFileSystemException e) {
      throw new IOException("Cannot read file from closed file system: " + file, e);
    }
  }

  /**
   * @deprecated Use {@link #load(CharSequence)}
   * <p>
   * Direct usage of element allows getting rid of {@link Document#getRootElement()} because only Element is required in mostly all cases.
   */
  @Deprecated
  public static @NotNull Document loadDocument(@NotNull InputStream stream) throws JDOMException, IOException {
    return loadDocumentUsingStaX(new InputStreamReader(stream, StandardCharsets.UTF_8));
  }

  @Contract("null -> null; !null -> !null")
  public static Element load(Reader reader) throws JDOMException, IOException {
    if (reader == null) {
      return null;
    }

    try {
      XMLStreamReader2 xmlStreamReader = StaxFactory.createXmlStreamReader(reader);
      try {
        return SafeStAXStreamBuilder.build(xmlStreamReader, true, true, SafeStAXStreamBuilder.FACTORY);
      }
      finally {
        xmlStreamReader.close();
      }
    }
    catch (XMLStreamException e) {
      throw new JDOMException(e.getMessage(), e);
    }
  }

  @Contract("null -> null; !null -> !null")
  public static Element load(InputStream stream) throws JDOMException, IOException {
    return stream == null ? null : loadUsingStaX(stream, null);
  }

  public static @NotNull Element load(byte @NotNull [] data) throws JDOMException, IOException {
    try {
      XMLStreamReader2 xmlStreamReader = StaxFactory.createXmlStreamReader(data);
      try {
        return SafeStAXStreamBuilder.build(xmlStreamReader, true, true, SafeStAXStreamBuilder.FACTORY);
      }
      finally {
        xmlStreamReader.close();
      }
    }
    catch (XMLStreamException e) {
      throw new JDOMException(e.getMessage(), e);
    }
  }

  @ApiStatus.Internal
  public static @NotNull Element load(@NotNull InputStream stream, @Nullable SafeJdomFactory factory) throws JDOMException, IOException {
    return loadUsingStaX(stream, factory);
  }

  public static @NotNull Element load(@NotNull Class<?> clazz, @NotNull String resource) throws JDOMException, IOException {
    InputStream stream = clazz.getResourceAsStream(resource);
    if (stream == null) {
      throw new FileNotFoundException(resource);
    }
    return load(stream);
  }

  /**
   * @deprecated Use {@link #load(CharSequence)}
   * <p>
   * Direct usage of element allows getting rid of {@link Document#getRootElement()} because only Element is required in mostly all cases.
   */
  @Deprecated
  public static @NotNull Document loadDocument(@NotNull URL url) throws JDOMException, IOException {
    return loadDocument(URLUtil.openStream(url));
  }

  public static @NotNull Element load(@NotNull URL url) throws JDOMException, IOException {
    return load(URLUtil.openStream(url));
  }

  public static @NotNull Element loadResource(@NotNull URL url) throws JDOMException, IOException {
    return load(URLUtil.openResourceStream(url));
  }

  public static void writeDocument(@NotNull Document document, @NotNull String filePath, String lineSeparator) throws IOException {
    try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(filePath))) {
      writeDocument(document, stream, lineSeparator);
    }
  }

  public static void writeDocument(@NotNull Document document, @NotNull File file, String lineSeparator) throws IOException {
    write(document, file, lineSeparator);
  }

  /**
   * @deprecated Use {@link #write(Element, Path)}
   */
  @Deprecated
  public static void write(@NotNull Element element, @NotNull File file) throws IOException {
    FileUtilRt.createParentDirs(file);
    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
      writeElement(element, writer, createOutputter("\n"));
    }
  }

  public static void write(@NotNull Element element, @NotNull Path file) throws IOException {
    Files.createDirectories(file.getParent());
    try (BufferedWriter writer = Files.newBufferedWriter(file)) {
      writeElement(element, writer, createOutputter("\n"));
    }
  }

  public static void write(@NotNull Parent element, @NotNull File file) throws IOException {
    write(element, file, "\n");
  }

  public static void write(@NotNull Parent element, @NotNull File file, @NotNull String lineSeparator) throws IOException {
    FileUtilRt.createParentDirs(file);

    try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(file))) {
      write(element, stream, lineSeparator);
    }
  }

  public static void writeDocument(@NotNull Document document, @NotNull OutputStream stream, String lineSeparator) throws IOException {
    write(document, stream, lineSeparator);
  }

  public static void write(@NotNull Parent element, @NotNull OutputStream stream, @NotNull String lineSeparator) throws IOException {
    try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
      if (element instanceof Document) {
        writeDocument((Document)element, writer, lineSeparator);
      }
      else {
        writeElement((Element)element, writer, lineSeparator);
      }
    }
  }

  public static @NotNull String writeDocument(@NotNull Document document, String lineSeparator) {
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

  public static @NotNull String write(@NotNull Element element) {
    return writeElement(element);
  }

  public static @NotNull String write(@NotNull Parent element, String lineSeparator) {
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
      writeElement((Element)element, writer, lineSeparator);
    }
    else if (element instanceof Document) {
      writeDocument((Document)element, writer, lineSeparator);
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

  public static @NotNull String writeElement(@NotNull Element element) {
    return writeElement(element, "\n");
  }

  public static @NotNull String writeElement(@NotNull Element element, String lineSeparator) {
    try {
      final StringWriter writer = new StringWriter();
      writeElement(element, writer, lineSeparator);
      return writer.toString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static @NotNull String writeChildren(final @NotNull Element element, final @NotNull String lineSeparator) throws IOException {
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

  public static @NotNull Format createFormat(@Nullable String lineSeparator) {
    return Format.getCompactFormat()
      .setIndent("  ")
      .setTextMode(Format.TextMode.TRIM)
      .setEncoding(CharsetToolkit.UTF8)
      .setOmitEncoding(false)
      .setOmitDeclaration(false)
      .setLineSeparator(lineSeparator);
  }

  public static @NotNull XMLOutputter createOutputter(String lineSeparator) {
    return new MyXMLOutputter(createFormat(lineSeparator));
  }

  /**
   * Returns null if no escapement necessary.
   */
  private static @Nullable String escapeChar(char c, boolean escapeApostrophes, boolean escapeSpaces, boolean escapeLineEnds) {
    switch (c) {
      case '\n':
        return escapeLineEnds ? "&#10;" : null;
      case '\r':
        return escapeLineEnds ? "&#13;" : null;
      case '\t':
        return escapeLineEnds ? "&#9;" : null;
      case ' ':
        return escapeSpaces ? "&#20" : null;
      case '<':
        return "&lt;";
      case '>':
        return "&gt;";
      case '\"':
        return "&quot;";
      case '\'':
        return escapeApostrophes ? "&apos;" : null;
      case '&':
        return "&amp;";
    }
    return null;
  }

  @Contract(pure = true)
  public static @NotNull String escapeText(@NotNull String text) {
    return escapeText(text, false, false);
  }

  @Contract(pure = true)
  public static @NotNull String escapeText(@NotNull String text, boolean escapeSpaces, boolean escapeLineEnds) {
    return escapeText(text, false, escapeSpaces, escapeLineEnds);
  }

  @Contract(pure = true)
  public static @NotNull String escapeText(@NotNull String text, boolean escapeApostrophes, boolean escapeSpaces, boolean escapeLineEnds) {
    StringBuilder buffer = null;
    for (int i = 0; i < text.length(); i++) {
      final char ch = text.charAt(i);
      final String quotation = escapeChar(ch, escapeApostrophes, escapeSpaces, escapeLineEnds);
      buffer = XmlStringUtil.appendEscapedSymbol(text, buffer, i, quotation, ch);
    }
    // If there were any entities, return the escaped characters
    // that we put in the StringBuffer. Otherwise, just return
    // the unmodified input string.
    return buffer == null ? text : buffer.toString();
  }

  private static final class MyXMLOutputter extends XMLOutputter {
    MyXMLOutputter(@NotNull Format format) {
      super(format);
    }

    @Override
    public @NotNull String escapeAttributeEntities(@NotNull String str) {
      return escapeText(str, false, true);
    }

    @Override
    public @NotNull String escapeElementEntities(@NotNull String str) {
      return escapeText(str, false, false);
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

  private static @NotNull ElementInfo getElementInfo(@NotNull Element element) {
    boolean hasNullAttributes = false;
    StringBuilder buf = new StringBuilder(element.getName());
    List<Attribute> attributes = getAttributes(element);
    int length = attributes.size();
    if (length > 0) {
      buf.append("[");
      for (int idx = 0; idx < length; idx++) {
        Attribute attr = attributes.get(idx);
        if (idx != 0) {
          buf.append(";");
        }
        buf.append(attr.getName());
        buf.append("=");
        buf.append(attr.getValue());
        if (attr.getValue() == null) {
          hasNullAttributes = true;
        }
      }
      buf.append("]");
    }
    return new ElementInfo(buf, hasNullAttributes);
  }

  public static void updateFileSet(File @NotNull [] oldFiles,
                                   String @NotNull [] newFilePaths,
                                   Document @NotNull [] newFileDocuments,
                                   String lineSeparator)
    throws IOException {
    getLogger().assertTrue(newFilePaths.length == newFileDocuments.length);

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

    List<String> writtenFilesPaths = new ArrayList<>();
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

  private static final class ElementInfo {
    final @NotNull CharSequence name;
    final boolean hasNullAttributes;

    private ElementInfo(@NotNull CharSequence name, boolean attributes) {
      this.name = name;
      hasNullAttributes = attributes;
    }
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
    return element == null || (!element.hasAttributes() && element.getContent().isEmpty());
  }

  public static boolean isEmpty(@Nullable Element element, int attributeCount) {
    return element == null || getAttributes(element).size() == attributeCount && element.getContent().isEmpty();
  }

  public static @NotNull List<Attribute> getAttributes(@NotNull Element e) {
    // avoid AttributeList creation if no attributes
    return e.hasAttributes() ? e.getAttributes() : Collections.emptyList();
  }

  @Contract("_, !null -> !null; !null, _ -> !null")
  public static @Nullable Element merge(@Nullable Element to, @Nullable Element from) {
    if (from == null) {
      return to;
    }
    if (to == null) {
      return from;
    }

    for (Iterator<Element> iterator = from.getChildren().iterator(); iterator.hasNext(); ) {
      Element configuration = iterator.next();
      iterator.remove();
      to.addContent(configuration);
    }
    for (Iterator<Attribute> iterator = getAttributes(from).iterator(); iterator.hasNext(); ) {
      Attribute attribute = iterator.next();
      iterator.remove();
      to.setAttribute(attribute);
    }
    return to;
  }

  public static @NotNull Element deepMerge(@NotNull Element to, @NotNull Element from) {
    for (Iterator<Element> iterator = from.getChildren().iterator(); iterator.hasNext(); ) {
      Element child = iterator.next();
      iterator.remove();

      Element existingChild = to.getChild(child.getName());
      if (existingChild != null && isEmpty(existingChild)) {
        // replace empty tag
        to.removeChild(child.getName());
        existingChild = null;
      }

      // if no children (e.g. `<module fileurl="value" />`), it means that element should be added as list item
      if (existingChild == null ||
          existingChild.getChildren().isEmpty() ||
          !isAttributesEqual(getAttributes(existingChild), getAttributes(child), false)) {
        to.addContent(child);
      }
      else {
        deepMerge(existingChild, child);
      }
    }
    for (Iterator<Attribute> iterator = getAttributes(from).iterator(); iterator.hasNext(); ) {
      Attribute attribute = iterator.next();
      iterator.remove();
      to.setAttribute(attribute);
    }
    return to;
  }

  public static @Nullable Element reduceChildren(@NotNull String name, @NotNull Element parent) {
    List<Element> children = parent.getChildren(name);
    Iterator<Element> it = children.iterator();
    if (!it.hasNext()) return null;
    Element accumulator = it.next();
    while (it.hasNext()) {
      merge(accumulator, it.next());
      it.remove();
    }
    return accumulator;
  }

  /**
   * Interns {@code element} to reduce instance count of many identical Elements created after loading JDOM document to memory.
   * For example, after interning <pre>{@code
   * <app>
   *   <component load="true" isDefault="true" name="comp1"/>
   *   <component load="true" isDefault="true" name="comp2"/>
   * </app>}</pre>
   * <p>
   * only one Attribute("load=true") will be created instead of two equivalent for each component tag, etc.
   *
   * <p><h3>Intended usage:</h3>
   * - When you need to keep some part of JDOM tree in memory, use this method before save the element to some collection,
   * E.g.: <pre>{@code
   *   public void readExternal(final Element element) {
   *     myStoredElement = JDOMUtil.internElement(element);
   *   }
   *   }</pre>
   * - When you need to save interned element back to JDOM and/or modify/add it, use {@link ImmutableElement#clone()}
   * to obtain mutable modifiable Element.
   * E.g.: <pre>{@code
   *   void writeExternal(Element element) {
   *     for (Attribute a : myStoredElement.getAttributes()) {
   *       element.setAttribute(a.getName(), a.getValue()); // String getters work as before
   *     }
   *     for (Element child : myStoredElement.getChildren()) {
   *       element.addContent(child.clone()); // need to call clone() before modifying/adding
   *     }
   *   }
   *   }</pre>
   *
   * @return interned Element, i.e. Element which<br/>
   * - is the same for equivalent parameters. E.g. two calls of internElement() with {@code <xxx/>} and the other {@code <xxx/>}
   * will return the same element {@code <xxx/>}<br/>
   * - getParent() method is not implemented (and will throw exception; interning would not make sense otherwise)<br/>
   * - is immutable (all modifications methods like setName(), setParent() etc. will throw)<br/>
   * - has {@code clone()} method which will return modifiable org.jdom.Element copy.<br/>
   */
  public static @NotNull Element internElement(@NotNull Element element) {
    return JDOMInterner.INSTANCE.internElement(element);
  }

  /**
   * Not required if you use XmlSerializator.
   *
   * @see XmlStringUtil#escapeIllegalXmlChars(String)
   */
  public static @NotNull String removeControlChars(@NotNull String text) {
    StringBuilder result = null;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!Verifier.isXMLCharacter(c)) {
        if (result == null) {
          result = new StringBuilder(text.length());
          result.append(text, 0, i);
        }
        continue;
      }

      if (result != null) {
        result.append(c);
      }
    }
    return result == null ? text : result.toString();
  }


  public static @Nullable Point getLocation(@Nullable Element element) {
    return element == null ? null : getLocation(element, X, Y);
  }

  public static @Nullable Point getLocation(@NotNull Element element, @NotNull String x, @NotNull String y) {
    String sX = element.getAttributeValue(x);
    if (sX == null) return null;
    String sY = element.getAttributeValue(y);
    if (sY == null) return null;
    try {
      return new Point(Integer.parseInt(sX), Integer.parseInt(sY));
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  public static @NotNull Element setLocation(@NotNull Element element, @NotNull Point location) {
    return setLocation(element, X, Y, location);
  }

  public static @NotNull Element setLocation(@NotNull Element element, @NotNull String x, @NotNull String y, @NotNull Point location) {
    return element
      .setAttribute(x, Integer.toString(location.x))
      .setAttribute(y, Integer.toString(location.y));
  }


  public static @Nullable Dimension getSize(@Nullable Element element) {
    return element == null ? null : getSize(element, WIDTH, HEIGHT);
  }

  public static @Nullable Dimension getSize(@NotNull Element element, @NotNull String width, @NotNull String height) {
    String sWidth = element.getAttributeValue(width);
    if (sWidth == null) return null;
    String sHeight = element.getAttributeValue(height);
    if (sHeight == null) return null;
    try {
      int iWidth = Integer.parseInt(sWidth);
      if (iWidth <= 0) return null;
      int iHeight = Integer.parseInt(sHeight);
      if (iHeight <= 0) return null;
      return new Dimension(iWidth, iHeight);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  public static @NotNull Element setSize(@NotNull Element element, @NotNull Dimension size) {
    return setSize(element, WIDTH, HEIGHT, size);
  }

  public static @NotNull Element setSize(@NotNull Element element, @NotNull String width, @NotNull String height, @NotNull Dimension size) {
    return element
      .setAttribute(width, Integer.toString(size.width))
      .setAttribute(height, Integer.toString(size.height));
  }


  public static @Nullable Rectangle getBounds(@Nullable Element element) {
    return element == null ? null : getBounds(element, X, Y, WIDTH, HEIGHT);
  }

  public static @Nullable Rectangle getBounds(@NotNull Element element,
                                              @NotNull String x,
                                              @NotNull String y,
                                              @NotNull String width,
                                              @NotNull String height) {
    String sX = element.getAttributeValue(x);
    if (sX == null) return null;
    String sY = element.getAttributeValue(y);
    if (sY == null) return null;
    String sWidth = element.getAttributeValue(width);
    if (sWidth == null) return null;
    String sHeight = element.getAttributeValue(height);
    if (sHeight == null) return null;
    try {
      int iWidth = Integer.parseInt(sWidth);
      if (iWidth <= 0) return null;
      int iHeight = Integer.parseInt(sHeight);
      if (iHeight <= 0) return null;
      return new Rectangle(Integer.parseInt(sX), Integer.parseInt(sY), iWidth, iHeight);
    }
    catch (NumberFormatException ignored) {
      return null;
    }
  }

  public static @NotNull Element setBounds(@NotNull Element element, @NotNull Rectangle bounds) {
    return setBounds(element, X, Y, WIDTH, HEIGHT, bounds);
  }

  public static @NotNull Element setBounds(@NotNull Element element,
                                           @NotNull String x,
                                           @NotNull String y,
                                           @NotNull String width,
                                           @NotNull String height,
                                           @NotNull Rectangle bounds) {
    return element
      .setAttribute(x, Integer.toString(bounds.x))
      .setAttribute(y, Integer.toString(bounds.y))
      .setAttribute(width, Integer.toString(bounds.width))
      .setAttribute(height, Integer.toString(bounds.height));
  }

  /**
   * Copies attributes and elements from {@code source} node to {@code target}
   * node if they are not present in the latter one.
   * <p>
   * Preserves {@code target} element's name.
   *
   * @param source the source element to copy from
   * @param target the target element to copy to
   */
  public static void copyMissingContent(@NotNull Element source, @NotNull Element target) {
    Element targetClone = target.clone();
    for (Attribute attribute : source.getAttributes()) {
      if (!hasAttribute(targetClone, attribute.getName())) {
        target.setAttribute(attribute.clone());
      }
    }
    for (Content content : source.getContent()) {
      if (!hasContent(targetClone, content)) {
        target.addContent(content.clone());
      }
    }
  }

  private static boolean hasAttribute(@NotNull Element element, @NotNull String name) {
    return element.getAttribute(name) != null;
  }

  private static boolean hasContent(@NotNull Element element, @NotNull Content content) {
    if (content instanceof Element) {
      return !element.getChildren(((Element)content).getName()).isEmpty();
    }
    else {
      return false;
    }
  }
}
