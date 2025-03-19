// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer.colors;

import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

@SuppressWarnings("UseJBColor")
public final class EclipseColorSchemeImporter implements SchemeImporter<EditorColorsScheme>, EclipseColorThemeElements {
  private static final String[] ECLIPSE_THEME_EXTENSIONS = {"xml"};

  private static final TextAttributesKey[] ATTRIBUTES_TO_COPY = {
    HighlighterColors.BAD_CHARACTER,
    CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES,
    CodeInsightColors.ERRORS_ATTRIBUTES,
    CodeInsightColors.WARNINGS_ATTRIBUTES,
    CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING,
    CodeInsightColors.DUPLICATE_FROM_SERVER,
    CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
    CodeInsightColors.INFORMATION_ATTRIBUTES,
    CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES,
    CodeInsightColors.LINE_FULL_COVERAGE,
    CodeInsightColors.LINE_PARTIAL_COVERAGE,
    CodeInsightColors.LINE_NONE_COVERAGE,
    ConsoleViewContentType.NORMAL_OUTPUT_KEY,
    ConsoleViewContentType.ERROR_OUTPUT_KEY,
    ConsoleViewContentType.USER_INPUT_KEY,
    ConsoleViewContentType.SYSTEM_OUTPUT_KEY,
    ConsoleViewContentType.LOG_ERROR_OUTPUT_KEY,
    ConsoleViewContentType.LOG_WARNING_OUTPUT_KEY,
    ConsoleViewContentType.LOG_EXPIRED_ENTRY,
    ConsoleHighlighter.BLACK,
    ConsoleHighlighter.RED,
    ConsoleHighlighter.GREEN,
    ConsoleHighlighter.YELLOW,
    ConsoleHighlighter.BLUE,
    ConsoleHighlighter.MAGENTA,
    ConsoleHighlighter.CYAN,
    ConsoleHighlighter.GRAY,
    ConsoleHighlighter.DARKGRAY,
    ConsoleHighlighter.RED_BRIGHT,
    ConsoleHighlighter.GREEN_BRIGHT,
    ConsoleHighlighter.YELLOW_BRIGHT,
    ConsoleHighlighter.BLUE_BRIGHT,
    ConsoleHighlighter.MAGENTA_BRIGHT,
    ConsoleHighlighter.CYAN_BRIGHT,
    ConsoleHighlighter.WHITE,

    DiffColors.DIFF_ABSENT,
    DiffColors.DIFF_CONFLICT,
    DiffColors.DIFF_DELETED,
    DiffColors.DIFF_INSERTED,
    DiffColors.DIFF_MODIFIED,

    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE,

    EditorColors.REFERENCE_HYPERLINK_COLOR,
    CodeInsightColors.HYPERLINK_ATTRIBUTES,
    CodeInsightColors.FOLLOWED_HYPERLINK_ATTRIBUTES
  };

  private static final ColorKey[] COLORS_TO_COPY = {
    EditorColors.ANNOTATIONS_COLOR,
    EditorColors.ANNOTATIONS_LAST_COMMIT_COLOR,
    EditorColors.ADDED_LINES_COLOR,
    EditorColors.MODIFIED_LINES_COLOR,
    EditorColors.DELETED_LINES_COLOR,
    EditorColors.WHITESPACES_MODIFIED_LINES_COLOR,
    EditorColors.BORDER_LINES_COLOR,
    EditorColors.IGNORED_ADDED_LINES_BORDER_COLOR,
    EditorColors.IGNORED_MODIFIED_LINES_BORDER_COLOR,
    EditorColors.IGNORED_DELETED_LINES_BORDER_COLOR,
  };

  //
  // These attributes are referenced only symbolically since they are located outside this module's dependencies.
  //
  private static final String @NonNls [] EXTERNAL_ATTRIBUTES = {
    "BREAKPOINT_ATTRIBUTES",
    "EXECUTIONPOINT_ATTRIBUTES",
    "NOT_TOP_FRAME_ATTRIBUTES",
    "DEBUGGER_INLINED_VALUES",
    "DEBUGGER_INLINED_VALUES_MODIFIED",
    "DEBUGGER_INLINED_VALUES_EXECUTION_LINE"
  };

  private static final String @NonNls [] EXTERNAL_COLORS = {
    "VCS_ANNOTATIONS_COLOR_1",
    "VCS_ANNOTATIONS_COLOR_2",
    "VCS_ANNOTATIONS_COLOR_3",
    "VCS_ANNOTATIONS_COLOR_4",
    "VCS_ANNOTATIONS_COLOR_5",
  };

  @Override
  public String @NotNull [] getSourceExtensions() {
    return ECLIPSE_THEME_EXTENSIONS;
  }

  @Override
  public @Nullable EditorColorsScheme importScheme(@NotNull Project project,
                                                   @NotNull VirtualFile selectedFile,
                                                   @NotNull EditorColorsScheme currentScheme,
                                                   @NotNull SchemeFactory<? extends EditorColorsScheme> schemeFactory) throws SchemeImportException {
    String themeName = readSchemeName(selectedFile);
    if (themeName != null) {
      EditorColorsScheme colorsScheme = schemeFactory.createNewScheme(themeName);
      readFromStream(selectedFile, new EclipseThemeOptionHandler(colorsScheme));
      setupMissingColors(colorsScheme);
      return colorsScheme;
    }
    return null;
  }

  private static String readSchemeName(@NotNull VirtualFile selectedFile) throws SchemeImportException {
    return readFromStream(selectedFile, null);
  }

  private static String readFromStream(final @NotNull VirtualFile file,
                                       final @Nullable EclipseThemeReader.OptionHandler optionHandler)
    throws SchemeImportException {
    try (InputStream inputStream = file.getInputStream()) {
      EclipseThemeReader themeReader = new EclipseThemeReader(optionHandler);
      themeReader.readSettings(inputStream);
      return themeReader.getThemeName();
    }
    catch (IOException e) {
      throw new SchemeImportException(e);
    }
  }

  private static void setupMissingColors(@NotNull EditorColorsScheme scheme) {
    Color background = scheme.getDefaultBackground();
    String defaultSchemeName = ColorUtil.isDark(background) ? "Darcula" : EditorColorsScheme.getDefaultSchemeName();
    EditorColorsScheme baseScheme = DefaultColorSchemesManager.getInstance().getScheme(defaultSchemeName);
    assert baseScheme != null : "Can not find default scheme '" + defaultSchemeName + "'!";
    for (TextAttributesKey attributesKey : ATTRIBUTES_TO_COPY) {
      copyAttributes(baseScheme, scheme, attributesKey);
    }
    for (ColorKey colorKey : COLORS_TO_COPY) {
      copyColor(baseScheme, scheme, colorKey);
    }
    for (String keyName : EXTERNAL_ATTRIBUTES) {
      TextAttributesKey key = TextAttributesKey.createTextAttributesKey(keyName);
      copyAttributes(baseScheme, scheme, key);
    }
    for (String keyName : EXTERNAL_COLORS) {
      ColorKey key = ColorKey.createColorKey(keyName);
      copyColor(baseScheme, scheme, key);
    }
    Color lightForeground = ColorUtil.mix(background, scheme.getDefaultForeground(), 0.5);
    scheme.setColor(EditorColors.WHITESPACES_COLOR, lightForeground);
    scheme.setColor(EditorColors.INDENT_GUIDE_COLOR, lightForeground);
    scheme.setColor(EditorColors.SOFT_WRAP_SIGN_COLOR, lightForeground);
    TextAttributes matchedBrace = new TextAttributes();
    matchedBrace.setEffectType(EffectType.BOXED);
    matchedBrace.setEffectColor(lightForeground);
    scheme.setAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES, matchedBrace);
    TextAttributes unmatchedBrace = matchedBrace.clone();
    Color errorColor = ColorUtil.mix(background, Color.RED, 0.5);
    unmatchedBrace.setEffectColor(errorColor);
    scheme.setAttributes(CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES, unmatchedBrace);

    TextAttributes markedForRemoval = scheme.getAttributes(CodeInsightColors.DEPRECATED_ATTRIBUTES);
    if (markedForRemoval != null) {
      markedForRemoval = markedForRemoval.clone();
      if (markedForRemoval.getEffectColor() == null) markedForRemoval.setEffectType(EffectType.STRIKEOUT);
      markedForRemoval.setEffectColor(errorColor);
      scheme.setAttributes(CodeInsightColors.MARKED_FOR_REMOVAL_ATTRIBUTES, markedForRemoval);
    }

    TextAttributes visibilityModifier = new TextAttributes();
    scheme.setAttributes(JavaHighlightingColors.PUBLIC_REFERENCE_ATTRIBUTES, visibilityModifier);
    scheme.setAttributes(JavaHighlightingColors.PRIVATE_REFERENCE_ATTRIBUTES, visibilityModifier);
    scheme.setAttributes(JavaHighlightingColors.PACKAGE_PRIVATE_REFERENCE_ATTRIBUTES, visibilityModifier);
    scheme.setAttributes(JavaHighlightingColors.PROTECTED_REFERENCE_ATTRIBUTES, visibilityModifier);
  }

  private static void copyAttributes(@NotNull EditorColorsScheme source, @NotNull EditorColorsScheme target, @NotNull TextAttributesKey key) {
    target.setAttributes(key, source.getAttributes(key));
  }

  private static void copyColor(@NotNull EditorColorsScheme source, @NotNull EditorColorsScheme target, @NotNull ColorKey key) {
    target.setColor(key, source.getColor(key));
  }
}
