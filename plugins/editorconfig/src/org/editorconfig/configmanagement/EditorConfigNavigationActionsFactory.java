// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.editor.EditorConfigPreviewManager;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.util.EditorConfigPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EditorConfigNavigationActionsFactory {
  private static final Key<EditorConfigNavigationActionsFactory> NAVIGATION_FACTORY_KEY = Key.create("editor.config.navigation.factory");

  private final List<String> myEditorConfigFilePaths = new ArrayList<>();

  private static final Object INSTANCE_LOCK = new Object();

  private EditorConfigNavigationActionsFactory() {
  }

  @NotNull
  public List<AnAction> getNavigationActions(@NotNull Project project, @NotNull VirtualFile sourceFile) {
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

  @NotNull
  private static String getActionName(@NotNull VirtualFile file, boolean withFolder) {
    final String fileName = EditorConfigPresentationUtil.getFileName(file, withFolder);
    return !withFolder ? EditorConfigBundle.message("action.open.file") : fileName;
  }

  @NotNull
  public static EditorConfigNavigationActionsFactory getInstance(@NotNull VirtualFile file) {
    synchronized (INSTANCE_LOCK) {
      EditorConfigNavigationActionsFactory instance = file.getUserData(NAVIGATION_FACTORY_KEY);
      if (instance == null) {
        instance = new EditorConfigNavigationActionsFactory();
        file.putUserData(NAVIGATION_FACTORY_KEY, instance);
      }
      return instance;
    }
  }

  private static class NavigationActionGroup extends ActionGroup {
    private final AnAction[] myChildActions;

    private NavigationActionGroup(AnAction[] actions) {
      super(EditorConfigBundle.message("action.open.file"), true);
      myChildActions = actions;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return myChildActions;
    }
  }

}
