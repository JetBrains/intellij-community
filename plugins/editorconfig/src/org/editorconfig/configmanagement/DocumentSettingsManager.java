package org.editorconfig.configmanagement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentSettingsManager extends FileDocumentManagerAdapter {
  // Handles the following EditorConfig settings:
  public static final String trimTrailingWhitespaceKey = "trim_trailing_whitespace";
  public static final String insertFinalNewlineKey = "insert_final_newline";
  private static final Map<String, String> trimMap;

  static {
    Map<String, String> map = new HashMap<>();
    map.put("true", EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    map.put("false", EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    trimMap = Collections.unmodifiableMap(map);
  }

  private static final Map<String, Boolean> newlineMap;

  static {
    Map<String, Boolean> map = new HashMap<>();
    map.put("true", Boolean.TRUE);
    map.put("false", Boolean.FALSE);
    newlineMap = Collections.unmodifiableMap(map);
  }

  private final Project myProject;

  public DocumentSettingsManager(Project project) {
    myProject = project;
  }

  @Override
  public void beforeDocumentSaving(@NotNull Document document) {
    // This is fired when any document is saved, regardless of whether it is part of a save-all or
    // a save-one operation
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    applySettings(file);
  }

  private void applySettings(VirtualFile file) {
    if (file == null) return;
    if (!Utils.isEnabled(CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings())) return;
    // Get editorconfig settings
    final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(myProject, file);
    // Apply trailing spaces setting
    final String trimTrailingWhitespace = Utils.configValueForKey(outPairs, trimTrailingWhitespaceKey);
    applyConfigValueToUserData(file, TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY,
                               trimTrailingWhitespaceKey, trimTrailingWhitespace, trimMap);
    // Apply final newline setting
    final String insertFinalNewline = Utils.configValueForKey(outPairs, insertFinalNewlineKey);
    applyConfigValueToUserData(file, TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY,
                               insertFinalNewlineKey, insertFinalNewline, newlineMap);
  }

  private <T> void applyConfigValueToUserData(VirtualFile file, Key<T> userDataKey, String editorConfigKey,
                                              String configValue, Map<String, T> configMap) {
    if (configValue.isEmpty()) {
      file.putUserData(userDataKey, null);
      return;
    }

    final T data = configMap.get(configValue);
    if (data == null) {
      Utils.invalidConfigMessage(myProject, configValue, editorConfigKey, file.getCanonicalPath());
    } else {
      file.putUserData(userDataKey, data);
    }
  }
}
