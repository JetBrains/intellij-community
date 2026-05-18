// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project.model.impl.module.content;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ProjectModelExternalSource;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JpsContentEntry implements ContentEntry, Disposable {
  private final VirtualFilePointer myRoot;
  private final JpsModule myModule;
  private final JpsRootModel myRootModel;
  private final List<JpsSourceFolder> mySourceFolders;
  private final List<JpsExcludeFolder> myExcludeFolders;
  private final List<String> myExcludePatterns;

  public JpsContentEntry(JpsModule module, JpsRootModel rootModel, String rootUrl) {
    myModule = module;
    myRootModel = rootModel;
    myRoot = VirtualFilePointerManager.getInstance().create(rootUrl, this, null);
    mySourceFolders = new ArrayList<>();
    var rootPath = VfsUtilCore.urlToPath(getUrl());
    for (var root : myModule.getSourceRoots()) {
      if (FileUtil.isAncestor(rootPath, VfsUtilCore.urlToPath(root.getUrl()), false)) {
        mySourceFolders.add(new JpsSourceFolder(root, this));
      }
    }
    myExcludeFolders = new ArrayList<>();
    for (var excludedUrl : myModule.getExcludeRootsList().getUrls()) {
      if (FileUtil.isAncestor(rootPath, VfsUtilCore.urlToPath(excludedUrl), false)) {
        myExcludeFolders.add(new JpsExcludeFolder(excludedUrl, this));
      }
    }
    myExcludePatterns = new SmartList<>();
    for (var pattern : myModule.getExcludePatterns()) {
      if (pattern.getBaseDirUrl().equals(rootUrl)) {
        myExcludePatterns.add(pattern.getPattern());
      }
    }
  }

  @Override
  public VirtualFile getFile() {
    return myRoot.getFile();
  }

  @Override
  public @NotNull String getUrl() {
    return myRoot.getUrl();
  }

  @Override
  public SourceFolder @NotNull [] getSourceFolders() {
    return mySourceFolders.toArray(new SourceFolder[0]);
  }

  @Override
  public @NotNull List<SourceFolder> getSourceFolders(@NotNull JpsModuleSourceRootType<?> rootType) {
    return getSourceFolders(Collections.singleton(rootType));
  }

  @Override
  public @NotNull List<SourceFolder> getSourceFolders(@NotNull Set<? extends JpsModuleSourceRootType<?>> rootTypes) {
    var folders = new SmartList<SourceFolder>();
    for (var folder : mySourceFolders) {
      if (rootTypes.contains(folder.getRootType())) {
        folders.add(folder);
      }
    }
    return folders;
  }

  @Override
  public VirtualFile @NotNull [] getSourceFolderFiles() {
    return getFiles(getSourceFolders());
  }

  private static VirtualFile[] getFiles(ContentFolder[] sourceFolders) {
    var result = new ArrayList<VirtualFile>(sourceFolders.length);
    for (var sourceFolder : sourceFolders) {
      var file = sourceFolder.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @Override
  public ExcludeFolder @NotNull [] getExcludeFolders() {
    return myExcludeFolders.toArray(new ExcludeFolder[0]);
  }

  @Override
  public @NotNull List<String> getExcludeFolderUrls() {
    var excluded = new ArrayList<String>();
    for (var folder : myExcludeFolders) {
      excluded.add(folder.getUrl());
    }
    for (var excludePolicy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(myRootModel.getProject())) {
      @SuppressWarnings("removal") var excludedRoots = excludePolicy.getExcludeRootsForModule(myRootModel);
      for (var pointer : excludedRoots) {
        excluded.add(pointer.getUrl());
      }
    }
    return excluded;
  }

  @Override
  public VirtualFile @NotNull [] getExcludeFolderFiles() {
    List<VirtualFile> excluded = new ArrayList<>();
    for (var folder : myExcludeFolders) {
      ContainerUtil.addIfNotNull(excluded, folder.getFile());
    }
    for (var excludePolicy : DirectoryIndexExcludePolicy.EP_NAME.getExtensions(myRootModel.getProject())) {
      @SuppressWarnings("removal") var excludedRoots = excludePolicy.getExcludeRootsForModule(myRootModel);
      for (var pointer : excludedRoots) {
        ContainerUtil.addIfNotNull(excluded, pointer.getFile());
      }
    }
    return VfsUtilCore.toVirtualFileArray(excluded);
  }

  @Override
  public @NotNull SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    return addSourceFolder(file, isTestSource, "");
  }

  @Override
  public @NotNull SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    return addSourceFolder(file.getUrl(), isTestSource, packagePrefix);
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(
    @NotNull VirtualFile file,
    @NotNull JpsModuleSourceRootType<P> type,
    @NotNull P properties
  ) {
    var sourceRoot = myModule.addSourceRoot(file.getUrl(), type, properties);
    var sourceFolder = new JpsSourceFolder(sourceRoot, this);
    mySourceFolders.add(sourceFolder);
    return sourceFolder;
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(file, type, type.createDefaultProperties());
  }

  private SourceFolder addSourceFolder(final String url, boolean isTestSource, String packagePrefix) {
    var rootType = isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    var properties = JpsJavaExtensionService.getInstance().createSourceRootProperties(packagePrefix);
    return addSourceFolder(url, rootType, properties);
  }

  @Override
  public @NotNull SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    return addSourceFolder(url, isTestSource, "");
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(url, type, type.createDefaultProperties());
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(
    @NotNull String url,
    @NotNull JpsModuleSourceRootType<P> type,
    @NotNull ProjectModelExternalSource externalSource
  ) {
    return addSourceFolder(url, type, type.createDefaultProperties());
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(
    @NotNull String url,
    @NotNull JpsModuleSourceRootType<P> type,
    boolean isAutomaticallyImported
  ) {
    return addSourceFolder(url, type, type.createDefaultProperties());
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(@NotNull  String url, @NotNull JpsModuleSourceRootType<P> type, @NotNull P properties) {
    var sourceRoot = myModule.addSourceRoot(url, type, properties);
    var sourceFolder = new JpsSourceFolder(sourceRoot, this);
    mySourceFolders.add(sourceFolder);
    return sourceFolder;
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(
    @NotNull String url,
    @NotNull JpsModuleSourceRootType<P> type,
    @NotNull P properties,
    boolean isAutomaticallyImported
  ) {
    return addSourceFolder(url, type, properties);
  }

  @Override
  public @NotNull <P extends JpsElement> SourceFolder addSourceFolder(
    @NotNull String url,
    @NotNull JpsModuleSourceRootType<P> type,
    @NotNull P properties,
    @Nullable ProjectModelExternalSource externalSource
  ) {
    return addSourceFolder(url, type, properties);
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    var folder = (JpsSourceFolder)sourceFolder;
    mySourceFolders.remove(folder);
    myModule.removeSourceRoot(folder.getSourceRoot().getUrl(), folder.getSourceRoot().getRootType());
    Disposer.dispose(folder);
  }

  @Override
  public void clearSourceFolders() {
    var toRemove = new ArrayList<JpsModuleSourceRoot>();
    for (var folder : mySourceFolders) {
      toRemove.add(folder.getSourceRoot());
      Disposer.dispose(folder);
    }
    mySourceFolders.clear();
    for (var root : toRemove) {
      myModule.removeSourceRoot(root.getUrl(), root.getRootType());
    }
  }

  @Override
  public @NotNull ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    return addExcludeFolder(file.getUrl());
  }

  @Override
  public @NotNull ExcludeFolder addExcludeFolder(@NotNull String url) {
    var folder = new JpsExcludeFolder(url, this);
    myModule.getExcludeRootsList().addUrl(url);
    myExcludeFolders.add(folder);
    return folder;
  }

  @Override
  public @NotNull ExcludeFolder addExcludeFolder(@NotNull String url, boolean isAutomaticallyImported) {
    return addExcludeFolder(url);
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    var folder = (JpsExcludeFolder)excludeFolder;
    myExcludeFolders.remove(folder);
    myModule.getExcludeRootsList().removeUrl(folder.getUrl());
    Disposer.dispose(folder);
  }

  @Override
  public boolean removeExcludeFolder(@NotNull String url) {
    for (var folder : myExcludeFolders) {
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
    var toRemove = new ArrayList<String>();
    for (var folder : myExcludeFolders) {
      toRemove.add(folder.getUrl());
      Disposer.dispose(folder);
    }
    myExcludeFolders.clear();
    for (var url : toRemove) {
      myModule.getExcludeRootsList().removeUrl(url);
    }
  }

  @Override
  public @NotNull List<String> getExcludePatterns() {
    return myExcludePatterns;
  }

  @Override
  public void addExcludePattern(@NotNull String pattern) {
    myExcludePatterns.add(pattern);
    myModule.addExcludePattern(getUrl(), pattern);
  }

  @Override
  public void removeExcludePattern(@NotNull String pattern) {
    myExcludePatterns.remove(pattern);
    myModule.removeExcludePattern(getUrl(), pattern);
  }

  @Override
  public void setExcludePatterns(@NotNull List<String> patterns) {
    for (var pattern : myExcludePatterns) {
      myModule.removeExcludePattern(getUrl(), pattern);
    }
    myExcludePatterns.clear();
    for (var pattern : patterns) {
      addExcludePattern(pattern);
    }
  }

  @Override
  public @NotNull ModuleRootModel getRootModel() {
    return myRootModel;
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public void dispose() {
    for (var folder : mySourceFolders) {
      Disposer.dispose(folder);
    }
    for (var folder : myExcludeFolders) {
      Disposer.dispose(folder);
    }
  }
}
