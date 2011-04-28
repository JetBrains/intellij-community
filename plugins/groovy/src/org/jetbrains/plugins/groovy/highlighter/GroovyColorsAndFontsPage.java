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
package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyIcons;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ven
 */
public class GroovyColorsAndFontsPage implements ColorSettingsPage {
  @NotNull
  public String getDisplayName() {
    return "Groovy";
  }

  @Nullable
  public Icon getIcon() {
    return GroovyIcons.GROOVY_ICON_16x16;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  private static final AttributesDescriptor[] ATTRS =
    new AttributesDescriptor[]{
      new AttributesDescriptor("Line comment", DefaultHighlighter.LINE_COMMENT),
      new AttributesDescriptor("Block comment", DefaultHighlighter.BLOCK_COMMENT),
      new AttributesDescriptor("Groovydoc comment", DefaultHighlighter.DOC_COMMENT_CONTENT),
      new AttributesDescriptor("Groovydoc tag", DefaultHighlighter.DOC_COMMENT_TAG),
      new AttributesDescriptor("Keyword", DefaultHighlighter.KEYWORD),
      new AttributesDescriptor("Number", DefaultHighlighter.NUMBER),
      new AttributesDescriptor("GString", DefaultHighlighter.GSTRING),
      new AttributesDescriptor("String", DefaultHighlighter.STRING),
      new AttributesDescriptor("Regular expression", DefaultHighlighter.REGEXP),
      new AttributesDescriptor("Braces", DefaultHighlighter.BRACES),
      new AttributesDescriptor("Brackets", DefaultHighlighter.BRACKETS),
      new AttributesDescriptor("Parentheses", DefaultHighlighter.PARENTHESES),
      new AttributesDescriptor("Operation sign", DefaultHighlighter.OPERATION_SIGN),
      new AttributesDescriptor("Bad character", DefaultHighlighter.BAD_CHARACTER),
      new AttributesDescriptor("Wrong string literal", DefaultHighlighter.WRONG_STRING),
      new AttributesDescriptor("Unresolved reference access", DefaultHighlighter.UNRESOLVED_ACCESS),
      new AttributesDescriptor("List/map to object conversion", DefaultHighlighter.LITERAL_CONVERSION),
      new AttributesDescriptor("Annotation", DefaultHighlighter.ANNOTATION),
      new AttributesDescriptor("Static field", DefaultHighlighter.STATIC_FIELD),
      new AttributesDescriptor("Instance field", DefaultHighlighter.INSTANCE_FIELD),
      new AttributesDescriptor("Instance method call", DefaultHighlighter.METHOD_CALL),
      new AttributesDescriptor("Static method call", DefaultHighlighter.STATIC_METHOD_ACCESS),
      new AttributesDescriptor("Class reference", DefaultHighlighter.CLASS_REFERENCE),
      new AttributesDescriptor("Map key accessed as a property", DefaultHighlighter.MAP_KEY),
      new AttributesDescriptor("Instance property reference", DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE),
      new AttributesDescriptor("Static property reference", DefaultHighlighter.STATIC_PROPERTY_REFERENCE),
    };

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new GroovySyntaxHighlighter();
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return "import <classref>javax.swing.JPanel</classref>\n" +
           "  ### \n" +
           "<gdoc>/**\n" +
           " * This is Groovydoc comment\n" +
           " * <doctag>@see</doctag> <classref>java.lang.String</classref>#equals\n" +
           " */</gdoc>\n" +
           "<annotation>@SpecialBean</annotation> \n" +
           "class <classref>Demo</classref> {\n" +
           "  def <instfield>property</instfield>\n" +
           "//This is a line comment\n" +
           "/* This is a block comment */\n" +
           "  static def foo(int i) {\n" +
           "    <classref>Map</classref> map = [key:1, b:2]\n" +
           "    print map.<mapkey>key</mapkey>\n" +
           "    return [i, i, <instfield>property</instfield>]\n" +
           "  }\n" +
           "  static def <statfield>panel</statfield> = new <classref>JPanel</classref>()\n" +
           "}\n" +
           "\n" +
           "<classref>Demo</classref>.<statfield>panel</statfield>.size = " +
           "<classref>Demo</classref>.<statmet>foo</statmet>(\"123${456}789\".toInteger()) \n" +
           "'JetBrains'.matches(/Jw+Bw+/) \n" +
           "def x=1 + <unresolved>unresolved</unresolved>\n" +
           "def f1 = []\n" +
           "<classref>File</classref> f=<literal>[</literal>'path'<literal>]</literal>\n" +
           "print new <classref>Demo</classref>().<prop>property</prop>"
      ;
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<String, TextAttributesKey>();
    map.put("annotation", DefaultHighlighter.ANNOTATION);
    map.put("statmet", DefaultHighlighter.STATIC_METHOD_ACCESS);
    map.put("statfield", DefaultHighlighter.STATIC_FIELD);
    map.put("instfield", DefaultHighlighter.INSTANCE_FIELD);
    map.put("gdoc", DefaultHighlighter.DOC_COMMENT_CONTENT);
    map.put("doctag", DefaultHighlighter.DOC_COMMENT_TAG);
    map.put("unresolved", DefaultHighlighter.UNRESOLVED_ACCESS);
    map.put("classref", DefaultHighlighter.CLASS_REFERENCE);
    map.put("literal", DefaultHighlighter.LITERAL_CONVERSION);
    map.put("mapkey", DefaultHighlighter.MAP_KEY);
    map.put("prop", DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE);
    map.put("staticprop", DefaultHighlighter.STATIC_PROPERTY_REFERENCE);
    return map;
  }
}
