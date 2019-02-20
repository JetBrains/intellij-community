// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement;

import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.editorconfig.core.DefaultParserCallback;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.editorconfig.language.util.EditorConfigPresentationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class EditorConfigNavigationActionsFactory extends DefaultParserCallback {
  private static final Key<EditorConfigNavigationActionsFactory> NAVIGATION_FACTORY_KEY = Key.create("editor.config.navigation.factory");

  private final List<String> myEditorConfigFilePaths = Collections.synchronizedList(ContainerUtil.newArrayList());

  private EditorConfigNavigationActionsFactory() {
  }

  @NotNull
  public List<AnAction> getNavigationActions(@NotNull Project project) {
    final List<AnAction> actions = ContainerUtil.newArrayList();
    final List<VirtualFile> editorConfigFiles = getEditorConfigFiles();
    for (VirtualFile editorConfigFile : getEditorConfigFiles()) {
      actions.add(DumbAwareAction.create(
        getActionName(editorConfigFile, editorConfigFiles.size() > 1),
        event -> OpenFileAction.openFile(editorConfigFile, project)));
    }
    return actions.size() <= 1 ? actions : Collections.singletonList(new NavigationActionGroup(actions.toArray(AnAction.EMPTY_ARRAY)));
  }

  @Override
  public boolean processEditorConfig(File configFile) {
    myEditorConfigFilePaths.add(configFile.getPath());
    return true;
  }

  @Override
  public boolean processFile(File file) {
    myEditorConfigFilePaths.clear();
    return true;
  }

  @NotNull
  private static String getActionName(@NotNull VirtualFile file, boolean withFolder) {
    final String fileName = EditorConfigPresentationUtil.getFileName(file, withFolder);
    return !withFolder ? EditorConfigBundle.message("action.open.file", fileName) : fileName;
  }

  @NotNull
  public List<VirtualFile> getEditorConfigFiles() {
    List<VirtualFile> files = ContainerUtil.newArrayList();
    synchronized (myEditorConfigFilePaths) {
      for (String path : myEditorConfigFilePaths) {
        VirtualFile file = VfsUtil.findFile(Paths.get(path), true);
        if (file != null) {
          files.add(file);
        }
      }
    }
    return files;
  }

  @NotNull
  public static EditorConfigNavigationActionsFactory getInstance(@NotNull VirtualFile file) {
    EditorConfigNavigationActionsFactory instance = file.getUserData(NAVIGATION_FACTORY_KEY);
    if (instance == null) {
      instance = new EditorConfigNavigationActionsFactory();
      file.putUserData(NAVIGATION_FACTORY_KEY, instance);
    }
    return instance;
  }

  private static class NavigationActionGroup extends ActionGroup {
    private final AnAction[] myChildActions;

    private NavigationActionGroup(AnAction[] actions) {
      super("Open EditorConfig File", true);
      myChildActions = actions;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return myChildActions;
    }
  }

}
