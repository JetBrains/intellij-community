/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
  @Override
  @NotNull
  public String getDisplayName() {
    return "Groovy";
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  private static final AttributesDescriptor[] ATTRS =
    new AttributesDescriptor[]{
      new AttributesDescriptor("Line comment", GroovySyntaxHighlighter.LINE_COMMENT),
      new AttributesDescriptor("Block comment", GroovySyntaxHighlighter.BLOCK_COMMENT),
      new AttributesDescriptor("Groovydoc comment", GroovySyntaxHighlighter.DOC_COMMENT_CONTENT),
      new AttributesDescriptor("Groovydoc tag", GroovySyntaxHighlighter.DOC_COMMENT_TAG),
      new AttributesDescriptor("Keyword", GroovySyntaxHighlighter.KEYWORD),
      new AttributesDescriptor("Number", GroovySyntaxHighlighter.NUMBER),
      new AttributesDescriptor("GString", GroovySyntaxHighlighter.GSTRING),
      new AttributesDescriptor("String", GroovySyntaxHighlighter.STRING),
      new AttributesDescriptor("Braces", GroovySyntaxHighlighter.BRACES),
      new AttributesDescriptor("Brackets", GroovySyntaxHighlighter.BRACKETS),
      new AttributesDescriptor("Parentheses", GroovySyntaxHighlighter.PARENTHESES),
      new AttributesDescriptor("Operation sign", GroovySyntaxHighlighter.OPERATION_SIGN),
      new AttributesDescriptor("Bad character", GroovySyntaxHighlighter.BAD_CHARACTER),
      //new AttributesDescriptor("Wrong string literal", GroovySyntaxHighlighter.WRONG_STRING),
      new AttributesDescriptor("Unresolved reference access", GroovySyntaxHighlighter.UNRESOLVED_ACCESS),
      new AttributesDescriptor("List/map to object conversion", GroovySyntaxHighlighter.LITERAL_CONVERSION),
      new AttributesDescriptor("Annotation", GroovySyntaxHighlighter.ANNOTATION),
      new AttributesDescriptor("Local variable", GroovySyntaxHighlighter.LOCAL_VARIABLE),
      new AttributesDescriptor("Reassigned local variable", GroovySyntaxHighlighter.REASSIGNED_LOCAL_VARIABLE),
      new AttributesDescriptor("Parameter", GroovySyntaxHighlighter.PARAMETER),
      new AttributesDescriptor("Reassigned parameter", GroovySyntaxHighlighter.REASSIGNED_PARAMETER),
      new AttributesDescriptor("Static field", GroovySyntaxHighlighter.STATIC_FIELD),
      new AttributesDescriptor("Instance field", GroovySyntaxHighlighter.INSTANCE_FIELD),
      new AttributesDescriptor("Constructor call", GroovySyntaxHighlighter.CONSTRUCTOR_CALL),
      new AttributesDescriptor("Instance method call", GroovySyntaxHighlighter.METHOD_CALL),
      new AttributesDescriptor("Static method call", GroovySyntaxHighlighter.STATIC_METHOD_ACCESS),
      new AttributesDescriptor("Method declaration", GroovySyntaxHighlighter.METHOD_DECLARATION),
      new AttributesDescriptor("Constructor declaration", GroovySyntaxHighlighter.CONSTRUCTOR_DECLARATION),
      new AttributesDescriptor("Class reference", GroovySyntaxHighlighter.CLASS_REFERENCE),
      new AttributesDescriptor("Type parameter reference", GroovySyntaxHighlighter.TYPE_PARAMETER),
      new AttributesDescriptor("Map key accessed as a property", GroovySyntaxHighlighter.MAP_KEY),
      new AttributesDescriptor("Instance property reference", GroovySyntaxHighlighter.INSTANCE_PROPERTY_REFERENCE),
      new AttributesDescriptor("Static property reference", GroovySyntaxHighlighter.STATIC_PROPERTY_REFERENCE),
      new AttributesDescriptor("Valid string escape", GroovySyntaxHighlighter.VALID_STRING_ESCAPE),
      new AttributesDescriptor("Invalid string escape", GroovySyntaxHighlighter.INVALID_STRING_ESCAPE),
      new AttributesDescriptor("Label", GroovySyntaxHighlighter.LABEL),
    };

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new GroovySyntaxHighlighter();
  }

  @Override
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

  @Override
  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    Map<String, TextAttributesKey> map = new HashMap<>();
    map.put("keyword", GroovySyntaxHighlighter.KEYWORD);
    map.put("annotation", GroovySyntaxHighlighter.ANNOTATION);
    map.put("statmet", GroovySyntaxHighlighter.STATIC_METHOD_ACCESS);
    map.put("instmet", GroovySyntaxHighlighter.METHOD_CALL);
    map.put("constructorCall", GroovySyntaxHighlighter.CONSTRUCTOR_CALL);
    map.put("statfield", GroovySyntaxHighlighter.STATIC_FIELD);
    map.put("instfield", GroovySyntaxHighlighter.INSTANCE_FIELD);
    map.put("gdoc", GroovySyntaxHighlighter.DOC_COMMENT_CONTENT);
    map.put("doctag", GroovySyntaxHighlighter.DOC_COMMENT_TAG);
    map.put("unresolved", GroovySyntaxHighlighter.UNRESOLVED_ACCESS);
    map.put("classref", GroovySyntaxHighlighter.CLASS_REFERENCE);
    map.put("typeparam", GroovySyntaxHighlighter.TYPE_PARAMETER);
    map.put("literal", GroovySyntaxHighlighter.LITERAL_CONVERSION);
    map.put("mapkey", GroovySyntaxHighlighter.MAP_KEY);
    map.put("prop", GroovySyntaxHighlighter.INSTANCE_PROPERTY_REFERENCE);
    map.put("staticprop", GroovySyntaxHighlighter.STATIC_PROPERTY_REFERENCE);
    map.put("validescape", GroovySyntaxHighlighter.VALID_STRING_ESCAPE);
    map.put("invalidescape", GroovySyntaxHighlighter.INVALID_STRING_ESCAPE);
    map.put("local", GroovySyntaxHighlighter.LOCAL_VARIABLE);
    map.put("reLocal", GroovySyntaxHighlighter.REASSIGNED_LOCAL_VARIABLE);
    map.put("param", GroovySyntaxHighlighter.PARAMETER);
    map.put("reParam", GroovySyntaxHighlighter.REASSIGNED_PARAMETER);
    map.put("method", GroovySyntaxHighlighter.METHOD_DECLARATION);
    map.put("constructor", GroovySyntaxHighlighter.CONSTRUCTOR_DECLARATION);
    map.put("label", GroovySyntaxHighlighter.LABEL);
    return map;
  }
}
