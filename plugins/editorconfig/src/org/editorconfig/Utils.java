package org.editorconfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.EditorConfigNotifier;
import org.editorconfig.settings.EditorConfigSettings;

import java.util.List;

public class Utils {
  public static String configValueForKey(List<OutPair> outPairs, String key) {
    for (OutPair outPair : outPairs) {
      if (outPair.getKey().equals(key)) {
        return outPair.getVal();
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
}
