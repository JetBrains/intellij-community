/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.project.model.impl.module.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.project.model.impl.module.JpsRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsContentEntry implements ContentEntry, Disposable {
  private final VirtualFilePointer myRoot;
  private final JpsModule myModule;
  private final JpsRootModel myRootModel;
  private List<JpsSourceFolder> mySourceFolders;
  private List<JpsExcludeFolder> myExcludeFolders;

  public JpsContentEntry(JpsModule module, JpsRootModel rootModel, String rootUrl) {
    myModule = module;
    myRootModel = rootModel;
    myRoot = VirtualFilePointerManager.getInstance().create(rootUrl, this, null);
    mySourceFolders = new ArrayList<JpsSourceFolder>();
    String rootPath = VfsUtilCore.urlToPath(getUrl());
    for (JpsModuleSourceRoot root : myModule.getSourceRoots()) {
      if (FileUtil.isAncestor(rootPath, VfsUtilCore.urlToPath(root.getUrl()), false)) {
        mySourceFolders.add(new JpsSourceFolder(root, this));
      }
    }
    myExcludeFolders = new ArrayList<JpsExcludeFolder>();
    for (String excludedUrl : myModule.getExcludeRootsList().getUrls()) {
      if (FileUtil.isAncestor(rootPath, VfsUtilCore.urlToPath(excludedUrl), false)) {
        myExcludeFolders.add(new JpsExcludeFolder(excludedUrl, this));
      }
    }
  }

  @Override
  public VirtualFile getFile() {
    return myRoot.getFile();
  }

  @NotNull
  @Override
  public String getUrl() {
    return myRoot.getUrl();
  }

  @Override
  public SourceFolder[] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[mySourceFolders.size()]);
  }

  @Override
  public VirtualFile[] getSourceFolderFiles() {
    return getFiles(getSourceFolders());
  }

  private static VirtualFile[] getFiles(ContentFolder[] sourceFolders) {
    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>(sourceFolders.length);
    for (ContentFolder sourceFolder : sourceFolders) {
      final VirtualFile file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public ExcludeFolder[] getExcludeFolders() {
    final ArrayList<ExcludeFolder> result = new ArrayList<ExcludeFolder>(myExcludeFolders);
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME,
                                                                              myRootModel.getProject())) {
      final VirtualFilePointer[] files = excludePolicy.getExcludeRootsForModule(myRootModel);
      for (VirtualFilePointer file : files) {
        addExcludeForOutputPath(file, result);
      }
    }
    if (myRootModel.isExcludeExplodedDirectory()) {
      addExcludeForOutputPath(myRootModel.myExplodedDirectoryPointer, result);
    }
    return result.toArray(new ExcludeFolder[result.size()]);
  }

  private void addExcludeForOutputPath(@Nullable final VirtualFilePointer outputPath, @NotNull ArrayList<ExcludeFolder> result) {
    if (outputPath == null) return;
    final VirtualFile outputPathFile = outputPath.getFile();
    final VirtualFile file = myRoot.getFile();
    if (outputPathFile != null && file != null && VfsUtilCore.isAncestor(file, outputPathFile, false)) {
      result.add(new JpsExcludeOutputFolder(outputPath.getUrl(), this));
    }
  }

  @Override
  public VirtualFile[] getExcludeFolderFiles() {
    return getFiles(getExcludeFolders());
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    throw new UnsupportedOperationException("'addSourceFolder' not implemented in " + getClass().getName());
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    throw new UnsupportedOperationException("'addSourceFolder' not implemented in " + getClass().getName());
  }

  @Override
  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    throw new UnsupportedOperationException("'addSourceFolder' not implemented in " + getClass().getName());
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearSourceFolders() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    throw new UnsupportedOperationException("'addExcludeFolder' not implemented in " + getClass().getName());
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    throw new UnsupportedOperationException("'addExcludeFolder' not implemented in " + getClass().getName());
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearExcludeFolders() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public void dispose() {
    for (JpsSourceFolder folder : mySourceFolders) {
      Disposer.dispose(folder);
    }
    for (JpsExcludeFolder folder : myExcludeFolders) {
      Disposer.dispose(folder);
    }
  }
}
