// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.icons.AllIcons;
import com.intellij.psi.codeStyle.IndentStatusBarUIContributor;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.settings.EditorConfigSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class EditorConfigStatusUIContributor extends IndentStatusBarUIContributor {

  private static final String PROJECT_ADVERTISEMENT_FLAG = "editor.config.ad.shown";

  private final boolean myEditorConfigIndentOptions;

  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("EditorConfig", NotificationDisplayType.STICKY_BALLOON, true);

  public EditorConfigStatusUIContributor(TransientCodeStyleSettings transientSettings) {
    super(getOverriddenIndentOptions(transientSettings));
    myEditorConfigIndentOptions = true;
  }

  public EditorConfigStatusUIContributor(IndentOptions options) {
    super(options);
    myEditorConfigIndentOptions = options.getFileIndentOptionsProvider() instanceof EditorConfigIndentOptionsProvider;
  }

  private static IndentOptions getOverriddenIndentOptions(@NotNull TransientCodeStyleSettings transientSettings) {
    PsiFile file = transientSettings.getPsiFile();
    return transientSettings.getLanguageIndentOptions(file.getLanguage());
  }

  @Override
  public boolean areActionsAvailable(@NotNull VirtualFile file) {
    return myEditorConfigIndentOptions;
  }

  @Nullable
  @Override
  public AnAction[] getActions(@NotNull PsiFile file) {
    if (myEditorConfigIndentOptions) {
      List<AnAction> actions = ContainerUtil.newArrayList();
      EditorConfigNavigationActionsFactory navigationActionsFactory =
        EditorConfigNavigationActionsFactory.getInstance(file.getVirtualFile());
      actions.addAll(navigationActionsFactory.getNavigationActions(file.getProject()));
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
        notifyCodeStyleChanged(project);
        showDisabledDetectionNotification(project);
      });
  }

  @Nullable
  @Override
  public String getHint() {
    return myEditorConfigIndentOptions ? "EditorConfig" : null;
  }

  @Nullable
  @Override
  public String getAdvertisementText(@NotNull PsiFile psiFile) {
    final PropertiesComponent projectProperties = PropertiesComponent.getInstance(psiFile.getProject());
    boolean adFlag = projectProperties.getBoolean(PROJECT_ADVERTISEMENT_FLAG);
    if (adFlag) return null;
    projectProperties.setValue(PROJECT_ADVERTISEMENT_FLAG, true);
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
      notifyCodeStyleChanged(myProject);
      myNotification.expire();
    }
  }

  @Override
  public boolean isShowFileIndentOptionsEnabled() {
    return false;
  }

  private static void notifyCodeStyleChanged(@NotNull Project project) {
    CodeStyleSettingsManager.getInstance(project).fireCodeStyleSettingsChanged(null);
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Editorconfig;
  }
}
