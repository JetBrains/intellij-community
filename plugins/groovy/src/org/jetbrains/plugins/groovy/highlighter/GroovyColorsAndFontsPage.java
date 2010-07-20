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
      new AttributesDescriptor(DefaultHighlighter.LINE_COMMENT_ID, DefaultHighlighter.LINE_COMMENT),
      new AttributesDescriptor(DefaultHighlighter.BLOCK_COMMENT_ID, DefaultHighlighter.BLOCK_COMMENT),
      new AttributesDescriptor(DefaultHighlighter.DOC_COMMENT_ID, DefaultHighlighter.DOC_COMMENT_CONTENT),
      new AttributesDescriptor(DefaultHighlighter.DOC_COMMENT_TAG_ID, DefaultHighlighter.DOC_COMMENT_TAG),
      new AttributesDescriptor(DefaultHighlighter.KEYWORD_ID, DefaultHighlighter.KEYWORD),
      new AttributesDescriptor(DefaultHighlighter.NUMBER_ID, DefaultHighlighter.NUMBER),
      new AttributesDescriptor(DefaultHighlighter.GSTRING_ID, DefaultHighlighter.GSTRING),
      new AttributesDescriptor(DefaultHighlighter.STRING_ID, DefaultHighlighter.STRING),
      new AttributesDescriptor(DefaultHighlighter.REGEXP_ID, DefaultHighlighter.REGEXP),
      new AttributesDescriptor(DefaultHighlighter.BRACES_ID, DefaultHighlighter.BRACES),
      new AttributesDescriptor(DefaultHighlighter.OPERATION_SIGN_ID, DefaultHighlighter.OPERATION_SIGN),
      new AttributesDescriptor(DefaultHighlighter.BAD_CHARACTER_ID, DefaultHighlighter.BAD_CHARACTER),
      new AttributesDescriptor(DefaultHighlighter.WRONG_STRING_ID, DefaultHighlighter.WRONG_STRING),
      new AttributesDescriptor(DefaultHighlighter.UNRESOLVED_ACCESS_ID, DefaultHighlighter.UNRESOLVED_ACCESS),
      new AttributesDescriptor(DefaultHighlighter.ANNOTATION_ID, DefaultHighlighter.ANNOTATION),
      new AttributesDescriptor(DefaultHighlighter.STATIC_FIELD_ID, DefaultHighlighter.STATIC_FIELD),
      new AttributesDescriptor(DefaultHighlighter.STATIC_FIELD_ID, DefaultHighlighter.INSTANCE_FIELD),
      new AttributesDescriptor(DefaultHighlighter.CLASS_REFERENCE_ID, DefaultHighlighter.CLASS_REFERENCE),
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
           "  static def foo(int i) { return [i, i, <instfield>property</instfield>] }\n" +
           "  static def <statfield>panel</statfield> = new <classref>JPanel</classref>()\n" +
           "}\n" +
           "\n" +
           "<classref>Demo</classref>.<statfield>panel</statfield>.size = " +
           "<classref>Demo</classref>.<statmet>foo</statmet>(\"123${456}789\".toInteger()) \n" +
           "'JetBrains'.matches(/Jw+Bw+/) \n" +
           "def x=1 + <unresolved>unresolved</unresolved>"      
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

    return map;
  }
}
