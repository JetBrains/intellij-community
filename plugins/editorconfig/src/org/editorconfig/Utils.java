// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.LineSeparator;
import org.editorconfig.configmanagement.EditorConfigIndentOptionsProvider;
import org.editorconfig.configmanagement.ConfigEncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.editorconfig.configmanagement.StandardEditorConfigProperties;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.plugincomponents.EditorConfigNotifier;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {

  public static final String EDITOR_CONFIG_FILE_NAME = ".editorconfig";

  public static final  String FULL_SETTINGS_SUPPORT_REG_KEY = "editor.config.full.settings.support";

  public static final String PLUGIN_ID = "org.editorconfig.editorconfigjetbrains";

  private static boolean ourIsFullSettingsSupportEnabledInTest;

  public static String configValueForKey(List<? extends OutPair> outPairs, String key) {
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

  public static boolean isEnabled(@NotNull Project project) {
    return isEnabled(CodeStyle.getSettings(project));
  }

  public static boolean isFullIntellijSettingsSupport() {
    return
      ourIsFullSettingsSupportEnabledInTest ||
      Registry.is(FULL_SETTINGS_SUPPORT_REG_KEY) && !EditorConfigRegistry.shouldSupportCSharp();
  }

  @TestOnly
  public static void setFullIntellijSettingsSupportEnabledInTest(boolean enabled) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourIsFullSettingsSupportEnabledInTest = enabled;
    }
  }

  public static void invalidConfigMessage(Project project, String configValue, String configKey, String filePath) {
    final String message = configValue != null ?
                           "\"" +
                           configValue +
                           "\" is not a valid value" +
                           (!configKey.isEmpty() ? " for " + configKey : "") +
                           " for file " +
                           filePath :
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

  public static String exportToString(Project project) {
    final CodeStyleSettings settings = CodeStyle.getSettings(project);
    final CommonCodeStyleSettings.IndentOptions commonIndentOptions = settings.getIndentOptions();
    StringBuilder result = new StringBuilder();
    addIndentOptions(result, "*", commonIndentOptions, getEncodingLine(project) +
                                                       getLineEndings(project) +
                                                       getTrailingSpacesLine() +
                                                       getEndOfFileLine());
    for (FileType fileType : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      if (!FileTypeIndex.containsFileOfType(fileType, GlobalSearchScope.allScope(project))) continue;

      final CommonCodeStyleSettings.IndentOptions options = settings.getIndentOptions(fileType);
      if (!equalIndents(commonIndentOptions, options)) {
        addIndentOptions(result, buildPattern(fileType), options, "");
      }
    }

    return result.toString();
  }

  public static void export(Project project) {
    final VirtualFile baseDir = project.getBaseDir();
    final VirtualFile child = baseDir.findChild(".editorconfig");
    if (child != null) {
      final String message = EditorConfigBundle.message("dialog.message.editorconfig.already.present.in.overwrite", baseDir.getPath());
      if (Messages.showYesNoDialog(project, message, EditorConfigBundle.message("dialog.title.editorconfig.exists"), null) == Messages.NO) return;
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        final VirtualFile editorConfig = baseDir.findOrCreateChildData(Utils.class, ".editorconfig");
        VfsUtil.saveText(editorConfig, exportToString(project));
      } catch (IOException e) {
        Logger.getInstance(Utils.class).error(e);
      }
    });
  }

  @NotNull
  private static String getEndOfFileLine() {
    return StandardEditorConfigProperties.INSERT_FINAL_NEWLINE + "=" + EditorSettingsExternalizable.getInstance().isEnsureNewLineAtEOF() + "\n";
  }

  @NotNull
  private static String getTrailingSpacesLine() {
    final Boolean trimTrailingSpaces = getTrimTrailingSpaces();
    return trimTrailingSpaces != null ? StandardEditorConfigProperties.TRIM_TRAILING_WHITESPACE + "=" + trimTrailingSpaces + "\n" : "";
  }

  @Nullable
  public static Boolean getTrimTrailingSpaces() {
    final String spaces = EditorSettingsExternalizable.getInstance().getStripTrailingSpaces();
    if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(spaces)) return false;
    if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE.equals(spaces)) return true;
    return null;
  }

  @NotNull
  private static String getLineEndings(@NotNull Project project) {
    final String separator = CodeStyle.getSettings(project).getLineSeparator();
    String s = getLineSeparatorString(separator);
    if (s != null) return LineEndingsManager.lineEndingsKey + "=" + s + "\n";
    return "";
  }

  @Nullable
  public static String getLineSeparatorString(@NotNull String separator) {
    for (LineSeparator s : LineSeparator.values()) {
      if (separator.equals(s.getSeparatorString())) {
        return StringUtil.toLowerCase(s.name());
      }
    }
    return null;
  }

  @NotNull
  public static String getEncodingLine(@NotNull Project project) {
    String encoding = getEncoding(project);
    return encoding != null ? ConfigEncodingManager.charsetKey + "=" + encoding + "\n" : "";
  }

  @Nullable
  public static String getEncoding(@NotNull Project project) {
    final Charset charset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    for (Map.Entry<String, Charset> entry : ConfigEncodingManager.encodingMap.entrySet()) {
      if (entry.getValue() == charset) {
        return entry.getKey();
      }
    }
    return null;
  }

  @NotNull
  public static String buildPattern(FileType fileType) {
    List<FileNameMatcher> associations = FileTypeManager.getInstance().getAssociations(fileType);
    String result = associations
            .stream()
            .map(matcher -> matcher.getPresentableString())
            .sorted()
            .collect(Collectors.joining(","));
    if (associations.size() > 1) {
      return "{" + result + "}";
    }
    return result;
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


  public static boolean editorConfigExists(@NotNull Project project) {
    SettingsProviderComponent settingsProvider  = SettingsProviderComponent.getInstance();
    String basePath = project.getBasePath();
    if (basePath == null) return false;
    File projectDir = new File(basePath);
    Set<String> rootDirs = settingsProvider.getRootDirs(project);
    if (rootDirs.isEmpty()) {
        rootDirs = Collections.singleton(basePath);
    }
    for (String rootDir : rootDirs) {
      File currRoot = new File(rootDir);
      while (currRoot != null) {
        if (containsEditorConfig(currRoot)) return true;
        if (EditorConfigRegistry.shouldStopAtProjectRoot() && FileUtil.filesEqual(currRoot, projectDir)) break;
        currRoot = currRoot.getParentFile();
      }
    }
    return false;
  }

  private static boolean containsEditorConfig(@NotNull File dir) {
    if (dir.exists() && dir.isDirectory()) {
      if (FileUtil.exists(dir.getPath() + File.separator + ".editorconfig")) return true;
    }
    return false;
  }

  @NotNull
  public static List<VirtualFile> pathsToFiles(@NotNull List<String> paths) {
    List<VirtualFile> files = new ArrayList<>();
    for (String path : paths) {
      VirtualFile file = VfsUtil.findFile(Paths.get(path), true);
      if (file != null) {
        files.add(file);
      }
    }
    return files;
  }
}