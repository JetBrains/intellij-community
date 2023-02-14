// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class ChangesBrowserModuleNode extends ChangesBrowserNode<Module> implements ChangesBrowserNode.NodeWithFilePath {
  @NotNull private final FilePath myModuleRoot;

  private ChangesBrowserModuleNode(@NotNull Module userObject, @NotNull FilePath moduleRoot) {
    super(userObject);
    myModuleRoot = moduleRoot;
  }

  @Nullable
  public static ChangesBrowserModuleNode create(@NotNull Module module) {
    FilePath moduleRoot = getModuleRootFilePath(module);
    if (moduleRoot == null) return null;
    return new ChangesBrowserModuleNode(module, moduleRoot);
  }

  @Override
  public void render(@NotNull final ChangesBrowserNodeRenderer renderer,
                     final boolean selected,
                     final boolean expanded,
                     final boolean hasFocus) {
    final Module module = (Module)userObject;

    renderer.append(module.isDisposed() ? "" : module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);

    appendParentPath(renderer, myModuleRoot);

    if (module.isDisposed()) {
      renderer.setIcon(ModuleType.EMPTY.getIcon());
    }
    else {
      renderer.setIcon(ModuleType.get(module).getIcon());
    }
  }

  @NotNull
  public FilePath getModuleRoot() {
    return myModuleRoot;
  }

  @Override
  public @NotNull FilePath getNodeFilePath() {
    return getModuleRoot();
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public int getSortWeight() {
    return MODULE_SORT_WEIGHT;
  }

  @Override
  public int compareUserObjects(final Module o2) {
    return compareFileNames(getUserObject().getName(), o2.getName());
  }

  @Nullable
  private static FilePath getModuleRootFilePath(@NotNull Module module) {
    return ReadAction.compute(() -> {
      if (module.isDisposed()) return null;
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      if (roots.length == 1) {
        return VcsUtil.getFilePath(roots[0]);
      }
      return VcsUtil.getFilePath(ModuleUtilCore.getModuleDirPath(module));
    });
  }
}
