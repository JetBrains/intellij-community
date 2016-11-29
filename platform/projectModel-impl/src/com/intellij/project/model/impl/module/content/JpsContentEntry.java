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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class JpsContentEntry implements ContentEntry, Disposable {
  private final VirtualFilePointer myRoot;
  private final JpsModule myModule;
  private final JpsRootModel myRootModel;
  private final List<JpsSourceFolder> mySourceFolders;
  private final List<JpsExcludeFolder> myExcludeFolders;

  public JpsContentEntry(JpsModule module, JpsRootModel rootModel, String rootUrl) {
    myModule = module;
    myRootModel = rootModel;
    myRoot = VirtualFilePointerManager.getInstance().create(rootUrl, this, null);
    mySourceFolders = new ArrayList<>();
    String rootPath = VfsUtilCore.urlToPath(getUrl());
    for (JpsModuleSourceRoot root : myModule.getSourceRoots()) {
      if (FileUtil.isAncestor(rootPath, VfsUtilCore.urlToPath(root.getUrl()), false)) {
        mySourceFolders.add(new JpsSourceFolder(root, this));
      }
    }
    myExcludeFolders = new ArrayList<>();
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

  @NotNull
  @Override
  public SourceFolder[] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[mySourceFolders.size()]);
  }

  @NotNull
  @Override
  public List<SourceFolder> getSourceFolders(@NotNull JpsModuleSourceRootType<?> rootType) {
    return getSourceFolders(Collections.singleton(rootType));
  }

  @NotNull
  @Override
  public List<SourceFolder> getSourceFolders(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    List<SourceFolder> folders = new SmartList<>();
    for (JpsSourceFolder folder : mySourceFolders) {
      if (rootTypes.contains(folder.getRootType())) {
        folders.add(folder);
      }
    }
    return folders;
  }

  @NotNull
  @Override
  public VirtualFile[] getSourceFolderFiles() {
    return getFiles(getSourceFolders());
  }

  private static VirtualFile[] getFiles(ContentFolder[] sourceFolders) {
    ArrayList<VirtualFile> result = new ArrayList<>(sourceFolders.length);
    for (ContentFolder sourceFolder : sourceFolders) {
      final VirtualFile file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public ExcludeFolder[] getExcludeFolders() {
    return myExcludeFolders.toArray(new ExcludeFolder[myExcludeFolders.size()]);
  }

  @NotNull
  @Override
  public List<String> getExcludeFolderUrls() {
    List<String> excluded = new ArrayList<>();
    for (JpsExcludeFolder folder : myExcludeFolders) {
      excluded.add(folder.getUrl());
    }
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions
      .getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myRootModel.getProject())) {
      for (VirtualFilePointer pointer : excludePolicy.getExcludeRootsForModule(myRootModel)) {
        excluded.add(pointer.getUrl());
      }
    }
    return excluded;
  }

  @NotNull
  @Override
  public VirtualFile[] getExcludeFolderFiles() {
    List<VirtualFile> excluded = new ArrayList<>();
    for (JpsExcludeFolder folder : myExcludeFolders) {
      ContainerUtil.addIfNotNull(excluded, folder.getFile());
    }
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, myRootModel.getProject())) {
      for (VirtualFilePointer pointer : excludePolicy.getExcludeRootsForModule(myRootModel)) {
        ContainerUtil.addIfNotNull(excluded, pointer.getFile());
      }
    }
    return VfsUtilCore.toVirtualFileArray(excluded);
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    return addSourceFolder(file, isTestSource, "");
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    return addSourceFolder(file.getUrl(), isTestSource, packagePrefix);
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull VirtualFile file,
                                                             @NotNull JpsModuleSourceRootType<P> type,
                                                             @NotNull P properties) {
    final JpsModuleSourceRoot sourceRoot = myModule.addSourceRoot(file.getUrl(), type, properties);
    final JpsSourceFolder sourceFolder = new JpsSourceFolder(sourceRoot, this);
    mySourceFolders.add(sourceFolder);
    return sourceFolder;
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(file, type, type.createDefaultProperties());
  }

  private SourceFolder addSourceFolder(final String url, boolean isTestSource, String packagePrefix) {
    final JavaSourceRootType rootType = isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties(packagePrefix);
    return addSourceFolder(url, rootType, properties);
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    return addSourceFolder(url, isTestSource, "");
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(url, type, type.createDefaultProperties());
  }

  @NotNull
  public  <P extends JpsElement> SourceFolder addSourceFolder(@NotNull  String url, @NotNull JpsModuleSourceRootType<P> type, @NotNull P properties) {
    final JpsModuleSourceRoot sourceRoot = myModule.addSourceRoot(url, type, properties);
    final JpsSourceFolder sourceFolder = new JpsSourceFolder(sourceRoot, this);
    mySourceFolders.add(sourceFolder);
    return sourceFolder;
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    final JpsSourceFolder folder = (JpsSourceFolder)sourceFolder;
    mySourceFolders.remove(folder);
    myModule.removeSourceRoot(folder.getSourceRoot().getUrl(), folder.getSourceRoot().getRootType());
    Disposer.dispose(folder);
  }

  @Override
  public void clearSourceFolders() {
    List<JpsModuleSourceRoot> toRemove = new ArrayList<>();
    for (JpsSourceFolder folder : mySourceFolders) {
      toRemove.add(folder.getSourceRoot());
      Disposer.dispose(folder);
    }
    mySourceFolders.clear();
    for (JpsModuleSourceRoot root : toRemove) {
      myModule.removeSourceRoot(root.getUrl(), root.getRootType());
    }
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    return addExcludeFolder(file.getUrl());
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    final JpsExcludeFolder folder = new JpsExcludeFolder(url, this);
    myModule.getExcludeRootsList().addUrl(url);
    myExcludeFolders.add(folder);
    return folder;
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    JpsExcludeFolder folder = (JpsExcludeFolder)excludeFolder;
    myExcludeFolders.remove(folder);
    myModule.getExcludeRootsList().removeUrl(folder.getUrl());
    Disposer.dispose(folder);
  }

  @Override
  public boolean removeExcludeFolder(@NotNull String url) {
    for (JpsExcludeFolder folder : myExcludeFolders) {
      if (folder.getUrl().equals(url)) {
        myExcludeFolders.remove(folder);
        myModule.getExcludeRootsList().removeUrl(url);
        Disposer.dispose(folder);
        return true;
      }
    }
    return false;
  }

  @Override
  public void clearExcludeFolders() {
    List<String> toRemove = new ArrayList<>();
    for (JpsExcludeFolder folder : myExcludeFolders) {
      toRemove.add(folder.getUrl());
      Disposer.dispose(folder);
    }
    myExcludeFolders.clear();
    for (String url : toRemove) {
      myModule.getExcludeRootsList().removeUrl(url);
    }
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
