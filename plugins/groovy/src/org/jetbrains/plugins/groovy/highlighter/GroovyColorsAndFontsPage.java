/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return JetgroovyIcons.Groovy.Groovy_16x16;
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
      new AttributesDescriptor("Braces", DefaultHighlighter.BRACES),
      new AttributesDescriptor("Brackets", DefaultHighlighter.BRACKETS),
      new AttributesDescriptor("Parentheses", DefaultHighlighter.PARENTHESES),
      new AttributesDescriptor("Operation sign", DefaultHighlighter.OPERATION_SIGN),
      new AttributesDescriptor("Bad character", DefaultHighlighter.BAD_CHARACTER),
      //new AttributesDescriptor("Wrong string literal", DefaultHighlighter.WRONG_STRING),
      new AttributesDescriptor("Unresolved reference access", DefaultHighlighter.UNRESOLVED_ACCESS),
      new AttributesDescriptor("List/map to object conversion", DefaultHighlighter.LITERAL_CONVERSION),
      new AttributesDescriptor("Annotation", DefaultHighlighter.ANNOTATION),
      new AttributesDescriptor("Local variable", DefaultHighlighter.LOCAL_VARIABLE),
      new AttributesDescriptor("Reassigned local variable", DefaultHighlighter.REASSIGNED_LOCAL_VARIABLE),
      new AttributesDescriptor("Parameter", DefaultHighlighter.PARAMETER),
      new AttributesDescriptor("Reassigned parameter", DefaultHighlighter.REASSIGNED_PARAMETER),
      new AttributesDescriptor("Static field", DefaultHighlighter.STATIC_FIELD),
      new AttributesDescriptor("Instance field", DefaultHighlighter.INSTANCE_FIELD),
      new AttributesDescriptor("Constructor call", DefaultHighlighter.CONSTRUCTOR_CALL),
      new AttributesDescriptor("Instance method call", DefaultHighlighter.METHOD_CALL),
      new AttributesDescriptor("Static method call", DefaultHighlighter.STATIC_METHOD_ACCESS),
      new AttributesDescriptor("Method declaration", DefaultHighlighter.METHOD_DECLARATION),
      new AttributesDescriptor("Constructor declaration", DefaultHighlighter.CONSTRUCTOR_DECLARATION),
      new AttributesDescriptor("Class reference", DefaultHighlighter.CLASS_REFERENCE),
      new AttributesDescriptor("Type parameter reference", DefaultHighlighter.TYPE_PARAMETER),
      new AttributesDescriptor("Map key accessed as a property", DefaultHighlighter.MAP_KEY),
      new AttributesDescriptor("Instance property reference", DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE),
      new AttributesDescriptor("Static property reference", DefaultHighlighter.STATIC_PROPERTY_REFERENCE),
      new AttributesDescriptor("Valid string escape", DefaultHighlighter.VALID_STRING_ESCAPE),
      new AttributesDescriptor("Invalid string escape", DefaultHighlighter.INVALID_STRING_ESCAPE),
      new AttributesDescriptor("Label", DefaultHighlighter.LABEL),
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
    return "<keyword>import</keyword> <classref>javax.swing.JPanel</classref>\n" +
           "  ### \n" +
           "<gdoc>/**\n" +
           " * This is Groovydoc comment\n" +
           " * <doctag>@see</doctag> <classref>java.lang.String</classref>#equals\n" +
           " */</gdoc>\n" +
           "<annotation>@SpecialBean</annotation> \n" +
           "<keyword>class</keyword> <classref>Demo</classref> {\n" +
           "  <keyword>public</keyword> <constructor>Demo</constructor>() {}\n" +
           "  <keyword>def</keyword> <instfield>property</instfield>\n" +
           "//This is a line comment\n" +
           "/* This is a block comment */\n" +
           "  <keyword>static</keyword> <keyword>def</keyword> <method>foo</method>(<keyword>int</keyword> <param>i</param>, <keyword>int</keyword> <reParam>j</reParam>) {\n" +
           "    <classref>Map</classref> <local>map</local> = [<mapkey>key</mapkey>:1, <mapkey>b</mapkey>:2]\n" +
           "    <reParam>j</reParam>++\n" +
           "    print map.<mapkey>key</mapkey>\n" +
           "    return [<param>i</param>, <instfield>property</instfield>]\n" +
           "  }\n" +
           "  <keyword>static</keyword> <keyword>def</keyword> <statfield>panel</statfield> = <keyword>new</keyword> <classref>JPanel</classref>()\n" +
           "  <keyword>def</keyword> <<typeparam>T</typeparam>> foo() {" +
           "    <typeparam>T</typeparam> list = <keyword>null</keyword>" +
           "  }\n" +
           "}\n" +
           "\n" +
           "<classref>Demo</classref>.<statfield>panel</statfield>.size = " +
           "<classref>Demo</classref>.<statmet>foo</statmet>(\"123${456}789\".<instmet>toInteger</instmet>()) \n" +
           "'JetBrains'.<instmet>matches</instmet>(/Jw+Bw+/) \n" +
           "<keyword>def</keyword> <local>x</local>=1 + <unresolved>unresolved</unresolved>\n" +
           "<label>label</label>:<keyword>def</keyword> <reLocal>f1</reLocal> = []\n" +
           "<reLocal>f1</reLocal> = [2]\n" +
           "<classref>File</classref> <local>f</local>=<literal>[</literal>'path'<literal>]</literal>\n" +
           "<instmet>print</instmet> <keyword>new</keyword> <constructorCall>Demo</constructorCall>().<prop>property</prop>\n" +
           "<instmet>print</instmet> '<validescape>\\n</validescape> <invalidescape>\\x</invalidescape>'"

      ;
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<String, TextAttributesKey>();
    map.put("keyword", DefaultHighlighter.KEYWORD);
    map.put("annotation", DefaultHighlighter.ANNOTATION);
    map.put("statmet", DefaultHighlighter.STATIC_METHOD_ACCESS);
    map.put("instmet", DefaultHighlighter.METHOD_CALL);
    map.put("constructorCall", DefaultHighlighter.CONSTRUCTOR_CALL);
    map.put("statfield", DefaultHighlighter.STATIC_FIELD);
    map.put("instfield", DefaultHighlighter.INSTANCE_FIELD);
    map.put("gdoc", DefaultHighlighter.DOC_COMMENT_CONTENT);
    map.put("doctag", DefaultHighlighter.DOC_COMMENT_TAG);
    map.put("unresolved", DefaultHighlighter.UNRESOLVED_ACCESS);
    map.put("classref", DefaultHighlighter.CLASS_REFERENCE);
    map.put("typeparam", DefaultHighlighter.TYPE_PARAMETER);
    map.put("literal", DefaultHighlighter.LITERAL_CONVERSION);
    map.put("mapkey", DefaultHighlighter.MAP_KEY);
    map.put("prop", DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE);
    map.put("staticprop", DefaultHighlighter.STATIC_PROPERTY_REFERENCE);
    map.put("validescape", DefaultHighlighter.VALID_STRING_ESCAPE);
    map.put("invalidescape", DefaultHighlighter.INVALID_STRING_ESCAPE);
    map.put("local", DefaultHighlighter.LOCAL_VARIABLE);
    map.put("reLocal", DefaultHighlighter.REASSIGNED_LOCAL_VARIABLE);
    map.put("param", DefaultHighlighter.PARAMETER);
    map.put("reParam", DefaultHighlighter.REASSIGNED_PARAMETER);
    map.put("method", DefaultHighlighter.METHOD_DECLARATION);
    map.put("constructor", DefaultHighlighter.CONSTRUCTOR_DECLARATION);
    map.put("label", DefaultHighlighter.LABEL);
    return map;
  }
}
