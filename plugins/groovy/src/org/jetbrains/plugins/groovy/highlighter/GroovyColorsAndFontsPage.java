/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
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
    return GroovyFileType.GROOVY_LOGO;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = new AttributesDescriptor[]{
      new AttributesDescriptor(DefaultHighlighter.LINE_COMMENT_ID, DefaultHighlighter.LINE_COMMENT),
      new AttributesDescriptor(DefaultHighlighter.BLOCK_COMMENT_ID, DefaultHighlighter.BLOCK_COMMENT),
      new AttributesDescriptor(DefaultHighlighter.KEYWORD_ID, DefaultHighlighter.KEYWORD),
      new AttributesDescriptor(DefaultHighlighter.NUMBER_ID, DefaultHighlighter.NUMBER),
      new AttributesDescriptor(DefaultHighlighter.STRING_ID, DefaultHighlighter.STRING),
      new AttributesDescriptor(DefaultHighlighter.REGEXP_ID, DefaultHighlighter.REGEXP),
      new AttributesDescriptor(DefaultHighlighter.BRACES_ID, DefaultHighlighter.BRACES),
      new AttributesDescriptor(DefaultHighlighter.OPERATION_SIGN_ID, DefaultHighlighter.OPERATION_SIGN),
      new AttributesDescriptor(DefaultHighlighter.BAD_CHARACTER_ID, DefaultHighlighter.BAD_CHARACTER),
      new AttributesDescriptor(DefaultHighlighter.WRONG_STRING_ID, DefaultHighlighter.WRONG_STRING),
      new AttributesDescriptor(DefaultHighlighter.UNTYPED_ACCESS_ID, DefaultHighlighter.UNTYPED_ACCESS),
    };
  }
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return new ColorDescriptor[0];
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return GroovyFileType.GROOVY_FILE_TYPE.getHighlighter(null, null);
  }

  @NonNls
  @NotNull
  public String getDemoText() {
    return "import javax.swing.JPanel\n" +
        "class Demo {\n" +
        "\\\\This is a line comment\n" +
        "/*This is a block comment*/\n" +
        "  JPanel panel = new JPanel()\n" +
        "  panel.size = [10, 10]\n" +
        "}";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
