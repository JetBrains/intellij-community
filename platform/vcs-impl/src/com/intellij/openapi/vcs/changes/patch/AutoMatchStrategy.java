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
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

abstract class AutoMatchStrategy {
  protected final VirtualFile myBaseDir;
  protected MultiMap<String, VirtualFile> myFolderDecisions;
  protected final List<TextFilePatchInProgress> myResult;

  AutoMatchStrategy(final VirtualFile baseDir) {
    myBaseDir = baseDir;
    myResult = new LinkedList<>();
    myFolderDecisions = MultiMap.createSet();
  }

  public abstract void acceptPatch(TextFilePatch patch, final Collection<VirtualFile> foundByName);

  public abstract void processCreation(TextFilePatch creation);

  public abstract void beforeCreations();

  public abstract boolean succeeded();

  public List<TextFilePatchInProgress> getResult() {
    return myResult;
  }

  protected void registerFolderDecision(final String patchPath, final VirtualFile base) {
    final String path = extractPathWithoutName(patchPath);
    if (path != null) {
      myFolderDecisions.putValue(path, base);
    }
  }

  @Nullable
  protected Collection<VirtualFile> suggestFolderForCreation(final TextFilePatch creation) {
    final String newFileParentPath = extractPathWithoutName(creation.getAfterName());
    if (newFileParentPath != null) {
      return filterVariants(creation, myFolderDecisions.get(newFileParentPath));
    }
    return null;
  }

  protected void processCreationBasedOnFolderDecisions(final TextFilePatch creation) {
    final Collection<VirtualFile> variants = suggestFolderForCreation(creation);
    if (variants != null) {
      myResult.add(new TextFilePatchInProgress(creation, variants, myBaseDir));
    }
    else {
      myResult.add(new TextFilePatchInProgress(creation, null, myBaseDir));
    }
  }

  protected Collection<VirtualFile> filterVariants(final TextFilePatch patch, final Collection<VirtualFile> in) {
    String path = patch.getBeforeName() == null ? patch.getAfterName() : patch.getBeforeName();
    path = path.replace("\\", "/");

    final boolean caseSensitive = SystemInfo.isFileSystemCaseSensitive;
    final Collection<VirtualFile> result = new LinkedList<>();
    for (VirtualFile vf : in) {
      final String vfPath = vf.getPath();
      if ((caseSensitive && vfPath.endsWith(path)) || ((!caseSensitive) && StringUtil.endsWithIgnoreCase(vfPath, path))) {
        result.add(vf);
      }
    }
    return result;
  }

  @Nullable
  protected String extractPathWithoutName(final String path) {
    final String replaced = path.replace("\\", "/");
    final int idx = replaced.lastIndexOf('/');
    if (idx == -1) return null;
    return replaced.substring(0, idx);
  }

  @Nullable
  protected TextFilePatchInProgress processMatch(final TextFilePatch patch, final VirtualFile file) {
    final String beforeName = patch.getBeforeName();
    if (beforeName == null) return null;
    final String[] parts = beforeName.replace('\\', '/').split("/");
    VirtualFile parent = file.getParent();
    int idx = parts.length - 2;
    while ((parent != null) && (idx >= 0)) {
      if (!parent.getName().equals(parts[idx])) {
        break;
      }
      parent = parent.getParent();
      --idx;
    }
    if (parent != null) {
      final TextFilePatchInProgress result = new TextFilePatchInProgress(patch, null, myBaseDir);
      result.setNewBase(parent);
      int numDown = idx + 1;
      processStipUp(result, numDown);
      return result;
    }
    return null;
  }

  public static void processStipUp(AbstractFilePatchInProgress patchInProgress, int num) {
    for (int i = 0; i < num; i++) {
      patchInProgress.up();
    }
  }
}
