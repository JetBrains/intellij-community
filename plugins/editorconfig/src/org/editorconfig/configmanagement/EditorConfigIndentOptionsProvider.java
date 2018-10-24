package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.Utils;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigIndentOptionsProvider extends FileIndentOptionsProvider {
  // Handles the following EditorConfig settings:
  public static final String indentSizeKey = "indent_size";
  public static final String continuationSizeKey = "continuation_indent_size";
  public static final String tabWidthKey = "tab_width";
  public static final String indentStyleKey = "indent_style";

  private static final Key<Boolean> PROJECT_ADVERTISEMENT_FLAG = Key.create("editor.config.ad.shown");

  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("EditorConfig", NotificationDisplayType.STICKY_BALLOON, true);

  @Nullable
  @Override
  public IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile psiFile) {
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    final Project project = psiFile.getProject();
    if (project.isDisposed() || !Utils.isEnabled(settings)) return null;

    // Get editorconfig settings
    final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(project, file);
    // Apply editorconfig settings for the current editor
    return applyCodeStyleSettings(project, outPairs, file, settings);
  }

  private static IndentOptions applyCodeStyleSettings(Project project,
                                                      final List<EditorConfig.OutPair> outPairs,
                                                      final VirtualFile file,
                                                      final CodeStyleSettings settings) {
    // Apply indent options
    final String indentSize = Utils.configValueForKey(outPairs, indentSizeKey);
    final String continuationIndentSize = Utils.configValueForKey(outPairs, continuationSizeKey);
    final String tabWidth = Utils.configValueForKey(outPairs, tabWidthKey);
    final String indentStyle = Utils.configValueForKey(outPairs, indentStyleKey);
    final IndentOptions indentOptions = (IndentOptions)settings.getIndentOptions(file.getFileType()).clone();
    if (applyIndentOptions(project, indentOptions, indentSize, continuationIndentSize, tabWidth, indentStyle, file.getCanonicalPath())) {
      indentOptions.setOverrideLanguageOptions(true);
      return indentOptions;
    }
    return null;
  }

  private static boolean applyIndentOptions(Project project, IndentOptions indentOptions,
                                            String indentSize, String continuationIndentSize, String tabWidth,
                                            String indentStyle, String filePath) {
    boolean changed = false;
    final String calculatedIndentSize = calculateIndentSize(tabWidth, indentSize);
    final String calculatedContinuationSize = calculateContinuationIndentSize(calculatedIndentSize, continuationIndentSize);
    final String calculatedTabWidth = calculateTabWidth(tabWidth, indentSize);
    if (!calculatedIndentSize.isEmpty()) {
      if (applyIndentSize(indentOptions, calculatedIndentSize)) {
        changed = true;
      } else {
        Utils.invalidConfigMessage(project, calculatedIndentSize, indentSizeKey, filePath);
      }
    }
    if (!calculatedContinuationSize.isEmpty()) {
      if (applyContinuationIndentSize(indentOptions, calculatedContinuationSize)) {
        changed = true;
      }
      else {
        Utils.invalidConfigMessage(project, calculatedIndentSize, indentSizeKey, filePath);
      }
    }
    if (!calculatedTabWidth.isEmpty()) {
      if (applyTabWidth(indentOptions, calculatedTabWidth)) {
        changed = true;
      }
      else {
        Utils.invalidConfigMessage(project, calculatedTabWidth, tabWidthKey, filePath);
      }
    }
    if (!indentStyle.isEmpty()) {
      if (applyIndentStyle(indentOptions, indentStyle)) {
        changed = true;
      }
      else {
        Utils.invalidConfigMessage(project, indentStyle, indentStyleKey, filePath);
      }
    }
    return changed;
  }

  private static String calculateIndentSize(final String tabWidth, final String indentSize) {
    return indentSize.equals("tab") ? tabWidth : indentSize;
  }

  private static String calculateContinuationIndentSize(final String indentSize, final String continuationIndentSize) {
    return continuationIndentSize.isEmpty() ? indentSize : continuationIndentSize;
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

  private static boolean applyIndentSize(final IndentOptions indentOptions, final String indentSize) {
    try {
      indentOptions.INDENT_SIZE = Integer.parseInt(indentSize);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean applyContinuationIndentSize(final IndentOptions indentOptions, final String continuationIndentSize) {
    try {
      indentOptions.CONTINUATION_INDENT_SIZE = Integer.parseInt(continuationIndentSize);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }
  private static boolean applyTabWidth(final IndentOptions indentOptions, final String tabWidth) {
    try {
      indentOptions.TAB_SIZE = Integer.parseInt(tabWidth);
      return true;
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  private static boolean applyIndentStyle(IndentOptions indentOptions, String indentStyle) {
    if (indentStyle.equals("tab") || indentStyle.equals("space")) {
      indentOptions.USE_TAB_CHARACTER = indentStyle.equals("tab");
      return true;
    }
    return false;
  }

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file, @NotNull IndentOptions indentOptions) {
    return isEditorConfigOptions(indentOptions);
  }

  @Nullable
  @Override
  public AnAction[] getActions(@NotNull PsiFile file, @NotNull IndentOptions indentOptions) {
    if (isEditorConfigOptions(indentOptions)) {
      List<AnAction> actions = ContainerUtil.newArrayList();
      actions.addAll(EditorConfigNavigationActionsFactory.getNavigationActions(file));
      return actions.toArray(AnAction.EMPTY_ARRAY);
    }
    return null;
  }

  @Nullable
  @Override
  public AnAction createDisableAction(@NotNull Project project) {
    return DumbAwareAction.create(
      EditorConfigBundle.message("action.disable"),
      e -> {
        EditorConfigSettings settings = CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings.class);
        settings.ENABLED = false;
        notifyIndentOptionsChanged(project, null);
        showDisabledDetectionNotification(project);
      });
  }

  @Nullable
  @Override
  public String getHint(@NotNull IndentOptions indentOptions) {
    return isEditorConfigOptions(indentOptions) ? "EditorConfig" : null;
  }

  private static boolean isEditorConfigOptions(@NotNull IndentOptions indentOptions) {
    return indentOptions.getFileIndentOptionsProvider() instanceof EditorConfigIndentOptionsProvider;
  }


  @Nullable
  @Override
  public String getAdvertisementText(@NotNull PsiFile psiFile, @NotNull IndentOptions indentOptions) {
    Project project = psiFile.getProject();
    Boolean adFlag = project.getUserData(PROJECT_ADVERTISEMENT_FLAG);
    if (adFlag != null && adFlag) return null;
    project.putUserData(PROJECT_ADVERTISEMENT_FLAG, true);
    return EditorConfigBundle.message("advertisement.text");
  }

  private static void showDisabledDetectionNotification(@NotNull Project project) {
    EditorConfigDisabledNotification notification = new EditorConfigDisabledNotification(project);
    notification.notify(project);
  }

  private static class EditorConfigDisabledNotification extends Notification {
    private EditorConfigDisabledNotification(Project project) {
      super(NOTIFICATION_GROUP.getDisplayId(),
            EditorConfigBundle.message("disabled.notification"), "",
            NotificationType.INFORMATION);
      addAction(new ReEnableAction(project, this));
      addAction(new ShowEditorConfigOption(ApplicationBundle.message("code.style.indent.provider.notification.settings")));
    }
  }

  private static class ShowEditorConfigOption extends DumbAwareAction {
    private ShowEditorConfigOption(@Nullable String text) {
      super(text);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      ShowSettingsUtilImpl.showSettingsDialog(e.getProject(), "preferences.sourceCode", "EditorConfig");
    }
  }

  private static class ReEnableAction extends DumbAwareAction {
    private final Project myProject;
    private final Notification myNotification;

    private ReEnableAction(@NotNull Project project, Notification notification) {
      super(ApplicationBundle.message("code.style.indent.provider.notification.re.enable"));
      myProject = project;
      myNotification = notification;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      EditorConfigSettings settings = CodeStyle.getSettings(myProject).getCustomSettings(EditorConfigSettings.class);
      settings.ENABLED = true;
      notifyIndentOptionsChanged(myProject, null);
      myNotification.expire();
    }
  }

  @Override
  public boolean isShowFileIndentOptionsEnabled() {
    return false;
  }
}
