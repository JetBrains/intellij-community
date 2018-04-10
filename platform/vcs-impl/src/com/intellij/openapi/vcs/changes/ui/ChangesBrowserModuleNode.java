// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ChangesBrowserModuleNode extends ChangesBrowserNode<Module> {
  @NotNull private final FilePath myModuleRoot;

  protected ChangesBrowserModuleNode(Module userObject) {
    super(userObject);

    myModuleRoot = getModuleRootFilePath(userObject);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final Module module = (Module)userObject;

    renderer.append(module.isDisposed() ? "" : module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);

    appendParentPath(renderer, myModuleRoot);

    if (module.isDisposed()) {
      renderer.setIcon(ModuleType.EMPTY.getIcon());
    } else {
      renderer.setIcon(ModuleType.get(module).getIcon());
    }
  }

  @NotNull
  public FilePath getModuleRoot() {
    return myModuleRoot;
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public int getSortWeight() {
    return DIRECTORY_PATH_SORT_WEIGHT;
  }

  @Override
  public int compareUserObjects(final Module o2) {
    return getUserObject().getName().compareToIgnoreCase(o2.getName());
  }

  @NotNull
  private static FilePath getModuleRootFilePath(Module module) {
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    if (roots.length == 1) {
      return VcsUtil.getFilePath(roots[0]);
    }
    return VcsUtil.getFilePath(ModuleUtilCore.getModuleDirPath(module));
  }
}
