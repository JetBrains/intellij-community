// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.editor.EditorConfigPreviewManager;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.util.EditorConfigPresentationUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EditorConfigNavigationActionsFactory {
  private static final Key<EditorConfigNavigationActionsFactory> NAVIGATION_FACTORY_KEY = Key.create("editor.config.navigation.factory");

  private final List<String> myEditorConfigFilePaths = new ArrayList<>();

  private static final Object INSTANCE_LOCK = new Object();

  private EditorConfigNavigationActionsFactory() {
  }

  public @NotNull List<AnAction> getNavigationActions(@NotNull Project project, @NotNull VirtualFile sourceFile) {
    final List<AnAction> actions = new ArrayList<>();
    synchronized (myEditorConfigFilePaths) {
      List<VirtualFile> editorConfigFiles = Utils.pathsToFiles(myEditorConfigFilePaths);
      for (VirtualFile editorConfigFile : editorConfigFiles) {
        if (editorConfigFile != null) {
          actions.add(DumbAwareAction.create(
            getActionName(editorConfigFile, editorConfigFiles.size() > 1),
            event -> openEditorConfig(project, sourceFile, editorConfigFile)));
        }
      }
    }
    return actions.size() <= 1 ? actions : Collections.singletonList(new NavigationActionGroup(actions.toArray(AnAction.EMPTY_ARRAY)));
  }

  private static void openEditorConfig(@NotNull Project project, @NotNull VirtualFile sourceFile, @NotNull VirtualFile editorConfigFile) {
    final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (fileEditorManager.isFileOpen(editorConfigFile)) {
      fileEditorManager.closeFile(editorConfigFile);
    }
    EditorConfigPreviewManager.getInstance(project).associateWithPreviewFile(editorConfigFile, sourceFile);
    fileEditorManager.openFile(editorConfigFile, true);
  }

  public void updateEditorConfigFilePaths(@NotNull List<String> editorConfigFilePaths) {
    synchronized (myEditorConfigFilePaths) {
      myEditorConfigFilePaths.clear();
      myEditorConfigFilePaths.addAll(editorConfigFilePaths);
    }
  }

  private static @NotNull @Nls String getActionName(@NotNull VirtualFile file, boolean withFolder) {
    final String fileName = EditorConfigPresentationUtil.getFileName(file, withFolder);
    return !withFolder ? EditorConfigBundle.message("action.open.file") : fileName;
  }

  public static @Nullable EditorConfigNavigationActionsFactory getInstance(@NotNull PsiFile psiFile) {
    final Project project = psiFile.getProject();
    final VirtualFile file = psiFile.getVirtualFile();
    synchronized (INSTANCE_LOCK) {
      UserDataHolder dataHolder = CodeStyleCachingService.getInstance(project).getDataHolder(file);
      EditorConfigNavigationActionsFactory instance = null;
      if (dataHolder !=null) {
        instance = dataHolder.getUserData(NAVIGATION_FACTORY_KEY);
        if (instance == null) {
          instance = new EditorConfigNavigationActionsFactory();
          dataHolder.putUserData(NAVIGATION_FACTORY_KEY, instance);
        }
      }
      return instance;
    }
  }

  private static final class NavigationActionGroup extends ActionGroup {
    private final AnAction[] myChildActions;

    private NavigationActionGroup(AnAction[] actions) {
      super(EditorConfigBundle.message("action.open.file"), true);
      myChildActions = actions;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      return myChildActions;
    }
  }

}
