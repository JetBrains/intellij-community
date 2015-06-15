/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;

/**
 * @author yole
 */
public class ChangesBrowserModuleNode extends ChangesBrowserNode<Module> {
  protected ChangesBrowserModuleNode(Module userObject) {
    super(userObject);
  }

  @Override
  public void render(final ChangesBrowserNodeRenderer renderer, final boolean selected, final boolean expanded, final boolean hasFocus) {
    final Module module = (Module)userObject;

    renderer.append(module.isDisposed() ? "" : module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);
    if (module.isDisposed()) {
      renderer.setIcon(ModuleType.EMPTY.getIcon());
    } else {
      renderer.setIcon(ModuleType.get(module).getIcon());
    }
  }

  @Override
  public String getTextPresentation() {
    return getUserObject().getName();
  }

  @Override
  public int getSortWeight() {
    return 3;
  }

  @Override
  public int compareUserObjects(final Object o2) {
    if (o2 instanceof Module) {
      return getUserObject().getName().compareToIgnoreCase(((Module) o2).getName());
    }

    return 0;
  }

  @Override
  public FilePath[] getFilePathsUnder() {
    final VirtualFile[] files = ModuleRootManager.getInstance(getUserObject()).getContentRoots();
    final FilePath[] result = new FilePath[files.length];
    for(int i=0; i<files.length; i++) {
      result [i] = VcsUtil.getFilePath(files[i]);
    }
    return result;
  }
}
