package org.editorconfig;

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.LineSeparator;
import org.editorconfig.configmanagement.EditorConfigIndentOptionsProvider;
import org.editorconfig.configmanagement.DocumentSettingsManager;
import org.editorconfig.configmanagement.EncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.EditorConfigNotifier;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Utils {
  public static String configValueForKey(List<OutPair> outPairs, String key) {
    for (OutPair outPair : outPairs) {
      if (outPair.getKey().equals(key)) {
        String val = outPair.getVal();
        return "none".equals(val) || "unset".equals(val) ? "" : val;
      }
    }
    return "";
  }

  public static boolean isEnabled(CodeStyleSettings currentSettings) {
    return currentSettings != null && currentSettings.getCustomSettings(EditorConfigSettings.class).ENABLED;
  }

  public static void invalidConfigMessage(Project project, String configValue, String configKey, String filePath) {
    final String message = configValue != null ?
                            "\"" + configValue + "\" is not a valid value" + (!configKey.isEmpty() ? " for " + configKey : "") + " for file " + filePath :
                            "Failed to read .editorconfig file";
    configValue = configValue != null ? configValue : "ioError";
    EditorConfigNotifier.getInstance().error(project, configValue, message);
  }

  public static String getFilePath(Project project, VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return project.getBasePath() + "/" + file.getNameWithoutExtension() + "." + file.getFileType().getDefaultExtension();
    }
    return file.getCanonicalPath();
  }

  public static void export(Project project) {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    final CommonCodeStyleSettings.IndentOptions commonIndentOptions = settings.getIndentOptions();
    StringBuilder result = new StringBuilder();
    addIndentOptions(result, "*", commonIndentOptions, getEncoding(project) +
                                                       getLineEndings(project) +
                                                       getTrailingSpaces() +
                                                       getEndOfFile());
    for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (!FileTypeIndex.containsFileOfType(fileType, GlobalSearchScope.allScope(project))) continue;

      final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(fileType);
      if (!equalIndents(commonIndentOptions, options)) {
        addIndentOptions(result, buildPattern(fileType), options, "");
      }
    }
    final VirtualFile baseDir = project.getBaseDir();
    final VirtualFile child = baseDir.findChild(".editorconfig");
    if (child != null) {
      final String message = ".editorconfig already present in " + baseDir.getPath() + "\nOverwrite?";
      if (Messages.showYesNoDialog(project, message, ".editorconfig exists", null) == Messages.NO) return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final VirtualFile editorConfig = baseDir.findOrCreateChildData(Utils.class, ".editorconfig");
        VfsUtil.saveText(editorConfig, result.toString());
      } catch (IOException e) {
        Logger.getInstance(Utils.class).error(e);
      }
    });
  }

  private static String getEndOfFile() {
    return DocumentSettingsManager.insertFinalNewlineKey + "=" + EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF() + "\n";
  }

  private static String getTrailingSpaces() {
    final String spaces = EditorSettingsExternalizable.getInstance().getStripTrailingSpaces();
    if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(spaces)) return DocumentSettingsManager.trimTrailingWhitespaceKey + "=false\n";
    if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE.equals(spaces)) return DocumentSettingsManager.trimTrailingWhitespaceKey + "=true\n";
    return "";
  }

  private static String getLineEndings(Project project) {
    final String separator = CodeStyleFacade.getInstance(project).getLineSeparator();
    for (LineSeparator s : LineSeparator.values()) {
      if (separator.equals(s.getSeparatorString())) {
        return LineEndingsManager.lineEndingsKey + "=" + s.name().toLowerCase(Locale.US) + "\n";
      }
    }
    return "";
  }

  @NotNull
  private static String getEncoding(Project project) {
    final Charset charset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    for (Map.Entry<String, Charset> entry : EncodingManager.encodingMap.entrySet()) {
      if (entry.getValue() == charset) {
        return EncodingManager.charsetKey + "=" + entry.getKey() + "\n";
      }
    }
    return "";
  }

  @NotNull
  private static String buildPattern(FileType fileType) {
    final StringBuilder result = new StringBuilder();
    final List<FileNameMatcher> associations = FileTypeManager.getInstance().getAssociations(fileType);
    for (FileNameMatcher matcher : associations) {
      if (result.length() != 0) result.append(",");
      result.append(matcher.getPresentableString());
    }
    if (associations.size() > 1) {
      result.insert(0, "{").append("}");
    }
    return result.toString();
  }

  private static boolean equalIndents(CommonCodeStyleSettings.IndentOptions commonIndentOptions,
                                      CommonCodeStyleSettings.IndentOptions options) {
    return options.USE_TAB_CHARACTER == commonIndentOptions.USE_TAB_CHARACTER &&
           options.TAB_SIZE == commonIndentOptions.TAB_SIZE &&
           options.INDENT_SIZE == commonIndentOptions.INDENT_SIZE;
  }

  private static void addIndentOptions(StringBuilder result,
                                       String pattern,
                                       CommonCodeStyleSettings.IndentOptions options,
                                       String additionalText) {
    if (pattern.isEmpty()) return;

    result.append("[").append(pattern).append("]").append("\n");
    result.append(additionalText);
    result.append(EditorConfigIndentOptionsProvider.indentStyleKey).append("=");
    if (options.USE_TAB_CHARACTER) {
      result.append("tab\n");
      result.append(EditorConfigIndentOptionsProvider.tabWidthKey).append("=").append(options.TAB_SIZE).append("\n");
    } else {
      result.append("space\n");
      result.append(EditorConfigIndentOptionsProvider.indentSizeKey).append("=").append(options.INDENT_SIZE).append("\n");
    }
    result.append("\n");
  }
}
