package org.editorconfig.configmanagement;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.List;

public class CodeStyleManager extends FileEditorManagerAdapter implements WindowFocusListener {
  // Handles the following EditorConfig settings:
  private static final String indentSizeKey = "indent_size";
  private static final String tabWidthKey = "tab_width";
  private static final String indentStyleKey = "indent_style";

  private static final Logger LOG = Logger.getInstance("#org.editorconfig.configmanagement.CodeStyleManager");
  private final CodeStyleSettingsManager codeStyleSettingsManager;
  private final Project project;

  public CodeStyleManager(Project project) {
    codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(project);
    this.project = project;
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    applySettings(file);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    final VirtualFile file = event.getNewFile();
    applySettings(file);
  }

  @Override
  public void windowGainedFocus(WindowEvent e) {
    final Editor currentEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (currentEditor != null) {
      final Document currentDocument = currentEditor.getDocument();
      final VirtualFile currentFile = FileDocumentManager.getInstance().getFile(currentDocument);
      applySettings(currentFile);
    }
  }

  @Override
  public void windowLostFocus(WindowEvent e) {
  }

  private void applySettings(final VirtualFile file) {
    if (file != null && file.isInLocalFileSystem()) {
      // Always drop any current temporary settings so that the defaults will be applied if
      // this is a non-editorconfig-managed file
      codeStyleSettingsManager.dropTemporarySettings();
      // Prepare a new settings object, which will maintain the standard settings if no
      // editorconfig settings apply
      final CodeStyleSettings currentSettings = codeStyleSettingsManager.getCurrentSettings();
      final CodeStyleSettings newSettings = new CodeStyleSettings();
      newSettings.copyFrom(currentSettings);
      // Get editorconfig settings
      final String filePath = file.getCanonicalPath();
      final SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
      final List<OutPair> outPairs = settingsProvider.getOutPairs(filePath);
      // Apply editorconfig settings for the current editor
      applyCodeStyleSettings(outPairs, newSettings, file);
      codeStyleSettingsManager.setTemporarySettings(newSettings);
      final EditorEx currentEditor = (EditorEx)FileEditorManager.getInstance(project).getSelectedTextEditor();
      if (currentEditor != null) {
        currentEditor.reinitSettings();
      }
    }
  }

  private static void applyCodeStyleSettings(final List<OutPair> outPairs, final CodeStyleSettings codeStyleSettings,
                                             final VirtualFile file) {
    // Apply indent options
    final String indentSize = Utils.configValueForKey(outPairs, indentSizeKey);
    final String tabWidth = Utils.configValueForKey(outPairs, tabWidthKey);
    final String indentStyle = Utils.configValueForKey(outPairs, indentStyleKey);
    final FileType fileType = file.getFileType();
    final Language language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() :
                              PlainTextLanguage.INSTANCE;
    final CommonCodeStyleSettings commonSettings = codeStyleSettings.getCommonSettings(language);
    final CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();
    applyIndentOptions(indentOptions, indentSize, tabWidth, indentStyle, file.getCanonicalPath());
  }

  private static void applyIndentOptions(CommonCodeStyleSettings.IndentOptions indentOptions,
                                         String indentSize, String tabWidth, String indentStyle, String filePath) {
    final String calculatedIndentSize = calculateIndentSize(tabWidth, indentSize);
    final String calculatedTabWidth = calculateTabWidth(tabWidth, indentSize);
    if (!calculatedIndentSize.isEmpty()) {
      if (applyIndentSize(indentOptions, calculatedIndentSize)) {
        LOG.debug(Utils.appliedConfigMessage(calculatedIndentSize, indentSizeKey, filePath));
      }
      else {
        LOG.warn(Utils.invalidConfigMessage(calculatedIndentSize, indentSizeKey, filePath));
      }
    }
    if (!calculatedTabWidth.isEmpty()) {
      if (applyTabWidth(indentOptions, calculatedTabWidth)) {
        LOG.debug(Utils.appliedConfigMessage(calculatedTabWidth, tabWidthKey, filePath));
      }
      else {
        LOG.warn(Utils.invalidConfigMessage(calculatedTabWidth, tabWidthKey, filePath));
      }
    }
    if (!indentStyle.isEmpty()) {
      if (applyIndentStyle(indentOptions, indentStyle)) {
        LOG.debug(Utils.appliedConfigMessage(indentStyle, indentStyleKey, filePath));
      }
      else {
        LOG.warn(Utils.invalidConfigMessage(indentStyle, indentStyleKey, filePath));
      }
    }
  }

  private static String calculateIndentSize(final String tabWidth, final String indentSize) {
    return indentSize.equals("tab") ? tabWidth : indentSize;
  }

  private static String calculateTabWidth(final String tabWidth, final String indentSize) {
    if (tabWidth.isEmpty() && indentSize.equals("tab")) {
      return "";
    }
    else if (tabWidth.isEmpty()) {
      return indentSize;
    }
    else {
      return tabWidth;
    }
  }

  private static boolean applyIndentSize(final CommonCodeStyleSettings.IndentOptions indentOptions, final String indentSize) {
    try {
      int indent = Integer.parseInt(indentSize);
      indentOptions.INDENT_SIZE = indent;
      indentOptions.CONTINUATION_INDENT_SIZE = indent;
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean applyTabWidth(final CommonCodeStyleSettings.IndentOptions indentOptions, final String tabWidth) {
    try {
      indentOptions.TAB_SIZE = Integer.parseInt(tabWidth);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean applyIndentStyle(CommonCodeStyleSettings.IndentOptions indentOptions, String indentStyle) {
    if (indentStyle.equals("tab") || indentStyle.equals("space")) {
      indentOptions.USE_TAB_CHARACTER = indentStyle.equals("tab");
      return true;
    }
    else {
      return false;
    }
  }
}
