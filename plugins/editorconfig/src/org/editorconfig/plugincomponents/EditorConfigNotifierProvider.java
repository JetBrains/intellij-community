package org.editorconfig.plugincomponents;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.LightColors;
import icons.EditorconfigIcons;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigNotifierProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("editor.config.notification.panel");
  private static final String EDITOR_CONFIG_ACCEPTED = "editor.config.accepted";

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull final FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return null;
    final Project project = ((TextEditor)fileEditor).getEditor().getProject();
    final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(project).getCurrentSettings();
    if (!Utils.isEnabled(settings) || PropertiesComponent.getInstance(project).getBoolean(EDITOR_CONFIG_ACCEPTED, false)) return null;

    final List<EditorConfig.OutPair> pairs = SettingsProviderComponent.getInstance().getOutPairs(project, Utils.getFilePath(project, file));
    if (!pairs.isEmpty()) {
      final EditorNotificationPanel panel = new EditorNotificationPanel() {
        @Override
        public Color getBackground() {
          return LightColors.GREEN;
        }
      }.text("EditorConfig is overriding Code Style settings for this file").
        icon(EditorconfigIcons.Editorconfig);
      panel.createActionLabel("OK", new Runnable() {
        @Override
        public void run() {
          PropertiesComponent.getInstance(project).setValue(EDITOR_CONFIG_ACCEPTED, "true");
          EditorNotifications.getInstance(project).updateAllNotifications();
        }
      });
      panel.createActionLabel("Disable EditorConfig support", new Runnable() {
        @Override
        public void run() {
          settings.getCustomSettings(EditorConfigSettings.class).ENABLED = false;
          EditorNotifications.getInstance(project).updateAllNotifications();
        }
      });
      return panel;
    }
    return null;
  }
}
