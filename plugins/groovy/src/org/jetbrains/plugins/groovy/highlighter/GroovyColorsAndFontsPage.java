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
        "class Panels {\n" +
        "\\\\This is line comment\n" +
        "/*This is block comment*/\n" +
        "  JPanel panel\n" +
        "  panel.size = [10, 10]\n" +
        "}";
  }

  @Nullable
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
