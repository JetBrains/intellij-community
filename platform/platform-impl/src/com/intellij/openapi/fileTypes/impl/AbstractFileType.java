// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.ide.highlighter.FileTypeRegistrar;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.CustomFileTypeEditor;
import com.intellij.lang.Commenter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.ExternalizableFileType;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.SmartList;
import com.intellij.util.text.StringTokenizer;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AbstractFileType extends UserFileType<AbstractFileType> implements ExternalizableFileType, ExternalizableScheme,
                                                                                CustomSyntaxTableFileType, PlainTextLikeFileType, AbstractFileTypeBase {
  private static final String SEMICOLON = ";";
  @NotNull
  private SyntaxTable mySyntaxTable;
  private SyntaxTable myDefaultSyntaxTable;
  private Commenter myCommenter;
  @NonNls static final String ELEMENT_HIGHLIGHTING = "highlighting";
  @NonNls private static final String ELEMENT_OPTIONS = "options";
  @NonNls private static final String ELEMENT_OPTION = "option";
  @NonNls private static final String ATTRIBUTE_VALUE = "value";
  @NonNls private static final String VALUE_LINE_COMMENT = "LINE_COMMENT";
  @NonNls private static final String VALUE_COMMENT_START = "COMMENT_START";
  @NonNls private static final String VALUE_COMMENT_END = "COMMENT_END";
  @NonNls private static final String VALUE_HEX_PREFIX = "HEX_PREFIX";
  @NonNls private static final String VALUE_NUM_POSTFIXES = "NUM_POSTFIXES";
  @NonNls private static final String VALUE_HAS_BRACES = "HAS_BRACES";
  @NonNls private static final String VALUE_HAS_BRACKETS = "HAS_BRACKETS";
  @NonNls private static final String VALUE_HAS_PARENS = "HAS_PARENS";
  @NonNls private static final String VALUE_HAS_STRING_ESCAPES = "HAS_STRING_ESCAPES";
  @NonNls private static final String VALUE_LINE_COMMENT_AT_START = "LINE_COMMENT_AT_START";
  @NonNls private static final String ELEMENT_KEYWORDS = "keywords";
  @NonNls private static final String ATTRIBUTE_IGNORE_CASE = "ignore_case";
  @NonNls private static final String ELEMENT_KEYWORD = "keyword";
  @NonNls private static final String ELEMENT_KEYWORDS2 = "keywords2";
  @NonNls private static final String ELEMENT_KEYWORDS3 = "keywords3";
  @NonNls private static final String ELEMENT_KEYWORDS4 = "keywords4";
  @NonNls private static final String ATTRIBUTE_NAME = "name";

  public AbstractFileType(@NotNull SyntaxTable syntaxTable) {
    mySyntaxTable = syntaxTable;
  }

  void initSupport() {
    for (FileTypeRegistrar registrar : FileTypeRegistrar.EP_NAME.getExtensions()) {
      registrar.initFileType(this);
    }
  }

  @Override
  public @NotNull SyntaxTable getSyntaxTable() {
    return mySyntaxTable;
  }

  public Commenter getCommenter() {
    return myCommenter;
  }

  public void setSyntaxTable(@NotNull SyntaxTable syntaxTable) {
    mySyntaxTable = syntaxTable;
  }

  @Override
  public AbstractFileType clone() {
    return (AbstractFileType)super.clone();
  }

  @Override
  public void copyFrom(@NotNull UserFileType<AbstractFileType> newType) {
    super.copyFrom(newType);

    if (newType instanceof AbstractFileType) {
      mySyntaxTable = ((CustomSyntaxTableFileType)newType).getSyntaxTable();
    }
  }

  @Override
  public boolean isBinary() {
    return false;
  }

  @Override
  public void readExternal(@NotNull Element typeElement) throws InvalidDataException {
    Element element = typeElement.getChild(ELEMENT_HIGHLIGHTING);
    if (element != null) {
      setSyntaxTable(readSyntaxTable(element));
    }
  }

  @NotNull
  static SyntaxTable readSyntaxTable(@NotNull Element root) {
    SyntaxTable table = new SyntaxTable();

    for (Element element : root.getChildren()) {
      if (ELEMENT_OPTIONS.equals(element.getName())) {
        for (final Element e : element.getChildren(ELEMENT_OPTION)) {
          String name = e.getAttributeValue(ATTRIBUTE_NAME);
          String value = e.getAttributeValue(ATTRIBUTE_VALUE);
          if (VALUE_LINE_COMMENT.equals(name)) {
            table.setLineComment(value);
          }
          else if (VALUE_COMMENT_START.equals(name)) {
            table.setStartComment(value);
          }
          else if (VALUE_COMMENT_END.equals(name)) {
            table.setEndComment(value);
          }
          else if (VALUE_HEX_PREFIX.equals(name)) {
            table.setHexPrefix(value);
          }
          else if (VALUE_NUM_POSTFIXES.equals(name)) {
            table.setNumPostfixChars(value);
          }
          else if (VALUE_LINE_COMMENT_AT_START.equals(name)) {
            table.lineCommentOnlyAtStart = Boolean.parseBoolean(value);
          }
          else if (VALUE_HAS_BRACES.equals(name)) {
            table.setHasBraces(Boolean.parseBoolean(value));
          }
          else if (VALUE_HAS_BRACKETS.equals(name)) {
            table.setHasBrackets(Boolean.parseBoolean(value));
          }
          else if (VALUE_HAS_PARENS.equals(name)) {
            table.setHasParens(Boolean.parseBoolean(value));
          }
          else if (VALUE_HAS_STRING_ESCAPES.equals(name)) {
            table.setHasStringEscapes(Boolean.parseBoolean(value));
          }
        }
      }
      else if (ELEMENT_KEYWORDS.equals(element.getName())) {
        boolean ignoreCase = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_IGNORE_CASE));
        table.setIgnoreCase(ignoreCase);
        loadKeywords(element, table.getKeywords1());
      }
      else if (ELEMENT_KEYWORDS2.equals(element.getName())) {
        loadKeywords(element, table.getKeywords2());
      }
      else if (ELEMENT_KEYWORDS3.equals(element.getName())) {
        loadKeywords(element, table.getKeywords3());
      }
      else if (ELEMENT_KEYWORDS4.equals(element.getName())) {
        loadKeywords(element, table.getKeywords4());
      }
    }

    return table;
  }

  private static void loadKeywords(@NotNull Element element, @NotNull Set<? super String> keywords) {
    String value = element.getAttributeValue(ELEMENT_KEYWORDS);
    if (value != null) {
      StringTokenizer tokenizer = new StringTokenizer(value, SEMICOLON);
      while(tokenizer.hasMoreElements()) {
        String keyword = tokenizer.nextToken().trim();
        if (!keyword.isEmpty()) keywords.add(keyword);
      }
    }
    for (final Element e : element.getChildren(ELEMENT_KEYWORD)) {
      keywords.add(e.getAttributeValue(ATTRIBUTE_NAME));
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    writeTable(element, getSyntaxTable());
  }

  private static void writeTable(@NotNull Element element, @NotNull SyntaxTable table) {
    Element highlightingElement = new Element(ELEMENT_HIGHLIGHTING);

    Element optionsElement = new Element(ELEMENT_OPTIONS);

    Element lineComment = new Element(ELEMENT_OPTION);
    lineComment.setAttribute(ATTRIBUTE_NAME, VALUE_LINE_COMMENT);
    lineComment.setAttribute(ATTRIBUTE_VALUE, table.getLineComment());
    optionsElement.addContent(lineComment);

    String commentStart = table.getStartComment();
    if (commentStart != null) {
      Element commentStartElement = new Element(ELEMENT_OPTION);
      commentStartElement.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_START);
      commentStartElement.setAttribute(ATTRIBUTE_VALUE, commentStart);
      optionsElement.addContent(commentStartElement);
    }

    String endComment = table.getEndComment();

    if (endComment != null) {
      Element commentEndElement = new Element(ELEMENT_OPTION);
      commentEndElement.setAttribute(ATTRIBUTE_NAME, VALUE_COMMENT_END);
      commentEndElement.setAttribute(ATTRIBUTE_VALUE, endComment);
      optionsElement.addContent(commentEndElement);
    }

    String prefix = table.getHexPrefix();

    if (prefix != null) {
      Element hexPrefix = new Element(ELEMENT_OPTION);
      hexPrefix.setAttribute(ATTRIBUTE_NAME, VALUE_HEX_PREFIX);
      hexPrefix.setAttribute(ATTRIBUTE_VALUE, prefix);
      optionsElement.addContent(hexPrefix);
    }

    String chars = table.getNumPostfixChars();

    if (chars != null) {
      Element numPostfixes = new Element(ELEMENT_OPTION);
      numPostfixes.setAttribute(ATTRIBUTE_NAME, VALUE_NUM_POSTFIXES);
      numPostfixes.setAttribute(ATTRIBUTE_VALUE, chars);
      optionsElement.addContent(numPostfixes);
    }

    addElementOption(optionsElement, VALUE_HAS_BRACES, table.isHasBraces());
    addElementOption(optionsElement, VALUE_HAS_BRACKETS, table.isHasBrackets());
    addElementOption(optionsElement, VALUE_HAS_PARENS, table.isHasParens());
    addElementOption(optionsElement, VALUE_HAS_STRING_ESCAPES, table.isHasStringEscapes());
    addElementOption(optionsElement, VALUE_LINE_COMMENT_AT_START, table.lineCommentOnlyAtStart);

    highlightingElement.addContent(optionsElement);

    writeKeywords(table.getKeywords1(), ELEMENT_KEYWORDS, highlightingElement).setAttribute(ATTRIBUTE_IGNORE_CASE, String.valueOf(table.isIgnoreCase()));
    writeKeywords(table.getKeywords2(), ELEMENT_KEYWORDS2, highlightingElement);
    writeKeywords(table.getKeywords3(), ELEMENT_KEYWORDS3, highlightingElement);
    writeKeywords(table.getKeywords4(), ELEMENT_KEYWORDS4, highlightingElement);

    element.addContent(highlightingElement);
  }

  private static void addElementOption(@NotNull Element optionsElement, @NotNull String valueHasParens, final boolean hasParens) {
    if (!hasParens) {
      return;
    }

    Element supportParens = new Element(ELEMENT_OPTION);
    supportParens.setAttribute(ATTRIBUTE_NAME, valueHasParens);
    supportParens.setAttribute(ATTRIBUTE_VALUE, String.valueOf(true));
    optionsElement.addContent(supportParens);
  }

  private static Element writeKeywords(@NotNull Set<String> keywords, @NotNull String tagName, @NotNull Element highlightingElement) {
    if (keywords.isEmpty() && !ELEMENT_KEYWORDS.equals(tagName)) return null;
    Element keywordsElement = new Element(tagName);
    String[] strings = ArrayUtilRt.toStringArray(keywords);
    Arrays.sort(strings);
    StringBuilder keywordsAttribute = new StringBuilder();

    for (final String keyword : strings) {
      if (!keyword.contains(SEMICOLON)) {
        if (keywordsAttribute.length() != 0) keywordsAttribute.append(SEMICOLON);
        keywordsAttribute.append(keyword);
      }
      else {
        Element e = new Element(ELEMENT_KEYWORD);
        e.setAttribute(ATTRIBUTE_NAME, keyword);
        keywordsElement.addContent(e);
      }
    }
    if (keywordsAttribute.length() != 0) {
      keywordsElement.setAttribute(ELEMENT_KEYWORDS, keywordsAttribute.toString());
    }
    highlightingElement.addContent(keywordsElement);
    return keywordsElement;
  }

  @Override
  public void markDefaultSettings() {
    myDefaultSyntaxTable = mySyntaxTable;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myDefaultSyntaxTable, getSyntaxTable());
  }

  @NonNls static final String ELEMENT_MAPPING = "mapping";
  @NonNls static final String ATTRIBUTE_EXT = "ext";
  @NonNls static final String ATTRIBUTE_PATTERN = "pattern";
  @NonNls static final String ATTRIBUTE_TYPE = "type";

  @NotNull
  static List<Pair<FileNameMatcher, String>> readAssociations(@NotNull Element element) {
    List<Element> children = element.getChildren(ELEMENT_MAPPING);
    if (children.isEmpty()) {
      return Collections.emptyList();
    }

    List<Pair<FileNameMatcher, String>> result = new SmartList<>();
    for (Element mapping : children) {
      String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
      String pattern = mapping.getAttributeValue(ATTRIBUTE_PATTERN);

      FileNameMatcher matcher = ext != null ? new ExtensionFileNameMatcher(ext) : FileTypeManager.parseFromString(pattern);
      result.add(Pair.create(matcher, mapping.getAttributeValue(ATTRIBUTE_TYPE)));
    }
    return result;
  }

  @Nullable
  static Element writeMapping(@NotNull String typeName, @NotNull FileNameMatcher matcher, boolean specifyTypeName) {
    Element mapping = new Element(ELEMENT_MAPPING);
    if (!writePattern(matcher, mapping)) {
      return null;
    }

    if (specifyTypeName) {
      mapping.setAttribute(ATTRIBUTE_TYPE, typeName);
    }

    return mapping;
  }

  // returns true if written
  static boolean writePattern(@NotNull FileNameMatcher matcher, @NotNull Element mapping) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_EXT, ((ExtensionFileNameMatcher)matcher).getExtension());
    }
    else if (matcher instanceof WildcardFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((WildcardFileNameMatcher)matcher).getPattern());
    }
    else if (matcher instanceof ExactFileNameMatcher) {
      mapping.setAttribute(ATTRIBUTE_PATTERN, ((ExactFileNameMatcher)matcher).getFileName());
    }
    else {
      return false;
    }
    return true;
  }

  @Override
  public SettingsEditor<AbstractFileType> getEditor() {
    return new CustomFileTypeEditor();
  }

  public void setCommenter(@NotNull Commenter commenter) {
    myCommenter = commenter;
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           obj instanceof AbstractFileType &&
           getName().equals(((AbstractFileType)obj).getName()) &&
           getDescription().equals(((AbstractFileType)obj).getDescription()) &&
           mySyntaxTable.equals(((AbstractFileType)obj).mySyntaxTable);
  }

  @Override
  public String toString() {
    return "AbstractFileType "+(getName().isEmpty() ? "" : getName()+"; ") +mySyntaxTable;
  }
}