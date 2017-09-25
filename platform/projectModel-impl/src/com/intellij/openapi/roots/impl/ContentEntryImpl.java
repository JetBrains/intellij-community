/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

import java.util.*;

/**
 *  @author dsl
 */
public class ContentEntryImpl extends RootModelComponentBase implements ContentEntry, ClonableContentEntry, Comparable<ContentEntryImpl> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleContentEntryImpl");
  @NotNull private final VirtualFilePointer myRoot;
  @NonNls public static final String ELEMENT_NAME = JpsModuleRootModelSerializer.CONTENT_TAG;
  private final Set<SourceFolder> mySourceFolders = new LinkedHashSet<>();
  private final Set<ExcludeFolder> myExcludeFolders = new TreeSet<>(ContentFolderComparator.INSTANCE);
  @NonNls public static final String URL_ATTRIBUTE = JpsModuleRootModelSerializer.URL_ATTRIBUTE;
  private List<String> myExcludePatterns;

  ContentEntryImpl(@NotNull VirtualFile file, @NotNull RootModelImpl m) {
    this(file.getUrl(), m);
  }

  ContentEntryImpl(@NotNull String url, @NotNull RootModelImpl m) {
    super(m);
    myRoot = VirtualFilePointerManager.getInstance().create(url, this, null);
  }

  ContentEntryImpl(@NotNull Element e, @NotNull RootModelImpl m) throws InvalidDataException {
    this(getUrlFrom(e), m);
    loadSourceFolders(e);
    loadExcludeFolders(e);
    loadExcludePatterns(e);
  }

  private void loadExcludePatterns(@NotNull Element e) {
    for (Element element : e.getChildren(JpsModuleRootModelSerializer.EXCLUDE_PATTERN_TAG)) {
      addExcludePattern(element.getAttributeValue(JpsModuleRootModelSerializer.EXCLUDE_PATTERN_ATTRIBUTE));
    }
  }

  private static String getUrlFrom(@NotNull Element e) throws InvalidDataException {
    LOG.assertTrue(ELEMENT_NAME.equals(e.getName()));

    String url = e.getAttributeValue(URL_ATTRIBUTE);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  private void loadSourceFolders(@NotNull Element e) throws InvalidDataException {
    for (Element child : e.getChildren(SourceFolderImpl.ELEMENT_NAME)) {
      addSourceFolder(new SourceFolderImpl(child, this));
    }
  }

  private void loadExcludeFolders(@NotNull Element e) throws InvalidDataException {
    for (Element child : e.getChildren(ExcludeFolderImpl.ELEMENT_NAME)) {
      ExcludeFolderImpl excludeFolder = new ExcludeFolderImpl(child, this);
      addExcludeFolder(excludeFolder);
    }
  }

  @Override
  public VirtualFile getFile() {
    //assert !isDisposed();
    return myRoot.getFile();
  }

  @Override
  @NotNull
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
    SmartList<SourceFolder> folders = new SmartList<>();
    for (SourceFolder folder : mySourceFolders) {
      if (rootTypes.contains(folder.getRootType())) {
        folders.add(folder);
      }
    }
    return folders;
  }

  @Override
  @NotNull
  public VirtualFile[] getSourceFolderFiles() {
    assert !isDisposed();
    final SourceFolder[] sourceFolders = getSourceFolders();
    ArrayList<VirtualFile> result = new ArrayList<>(sourceFolders.length);
    for (SourceFolder sourceFolder : sourceFolders) {
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
    //assert !isDisposed();
    return myExcludeFolders.toArray(new ExcludeFolder[myExcludeFolders.size()]);
  }

  @NotNull
  @Override
  public List<String> getExcludeFolderUrls() {
    List<String> excluded = new ArrayList<>();
    for (ExcludeFolder folder : myExcludeFolders) {
      excluded.add(folder.getUrl());
    }
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, getRootModel().getProject())) {
      for (VirtualFilePointer pointer : excludePolicy.getExcludeRootsForModule(getRootModel())) {
        excluded.add(pointer.getUrl());
      }
    }
    return excluded;
  }

  @Override
  @NotNull
  public VirtualFile[] getExcludeFolderFiles() {
    assert !isDisposed();
    ArrayList<VirtualFile> result = new ArrayList<>();
    for (ExcludeFolder excludeFolder : getExcludeFolders()) {
      ContainerUtil.addIfNotNull(result, excludeFolder.getFile());
    }
    for (DirectoryIndexExcludePolicy excludePolicy : Extensions.getExtensions(DirectoryIndexExcludePolicy.EP_NAME, getRootModel().getProject())) {
      for (VirtualFilePointer pointer : excludePolicy.getExcludeRootsForModule(getRootModel())) {
        ContainerUtil.addIfNotNull(result, pointer.getFile());
      }
    }
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource) {
    return addSourceFolder(file, isTestSource, SourceFolderImpl.DEFAULT_PACKAGE_PREFIX);
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix) {
    JavaSourceRootType type = isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    return addSourceFolder(file, type);
  }

  @Override
  @NotNull
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type,
                                                             @NotNull P properties) {
    assertCanAddFolder(file);
    return addSourceFolder(new SourceFolderImpl(file, JpsElementFactory.getInstance().createModuleSourceRoot(file.getUrl(), type, properties), this));
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull VirtualFile file, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(file, type, type.createDefaultProperties());
  }

  @NotNull
  @Override
  public SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource) {
    return addSourceFolder(url, isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull String url, @NotNull JpsModuleSourceRootType<P> type) {
    return addSourceFolder(url, type, type.createDefaultProperties());
  }

  @NotNull
  @Override
  public <P extends JpsElement> SourceFolder addSourceFolder(@NotNull String url,
                                                             @NotNull JpsModuleSourceRootType<P> type,
                                                             @NotNull P properties) {
    assertFolderUnderMe(url);
    JpsModuleSourceRoot sourceRoot = JpsElementFactory.getInstance().createModuleSourceRoot(url, type, properties);
    return addSourceFolder(new SourceFolderImpl(sourceRoot, this));
  }

  @NotNull
  private SourceFolder addSourceFolder(@NotNull SourceFolderImpl f) {
    mySourceFolders.add(f);
    Disposer.register(this, f); //rewire source folder dispose parent from root model to this content root
    return f;
  }

  @Override
  public void removeSourceFolder(@NotNull SourceFolder sourceFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(sourceFolder, mySourceFolders);
    doRemove(sourceFolder);
  }

  private void doRemove(SourceFolder sourceFolder) {
    mySourceFolders.remove(sourceFolder);
    Disposer.dispose((Disposable)sourceFolder);
  }

  @Override
  public void clearSourceFolders() {
    assert !isDisposed();
    getRootModel().assertWritable();
    for (SourceFolder folder : mySourceFolders) {
      Disposer.dispose((Disposable)folder);
    }
    mySourceFolders.clear();
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull VirtualFile file) {
    assert !isDisposed();
    assertCanAddFolder(file);
    return addExcludeFolder(new ExcludeFolderImpl(file, this));
  }

  @Override
  public ExcludeFolder addExcludeFolder(@NotNull String url) {
    assert !isDisposed();
    assertCanAddFolder(url);
    return addExcludeFolder(new ExcludeFolderImpl(url, this));
  }

  private void assertCanAddFolder(@NotNull VirtualFile file) {
    assertCanAddFolder(file.getUrl());
  }

  private void assertCanAddFolder(@NotNull String url) {
    getRootModel().assertWritable();
    assertFolderUnderMe(url);
  }

  @Override
  public void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder) {
    assert !isDisposed();
    assertCanRemoveFrom(excludeFolder, myExcludeFolders);
    myExcludeFolders.remove(excludeFolder);
    Disposer.dispose((Disposable)excludeFolder);
  }

  @Override
  public boolean removeExcludeFolder(@NotNull String url) {
    for (ExcludeFolder folder : myExcludeFolders) {
      if (folder.getUrl().equals(url)) {
        myExcludeFolders.remove(folder);
        Disposer.dispose((Disposable)folder);
        return true;
      }
    }
    return false;
  }

  @Override
  public void clearExcludeFolders() {
    assert !isDisposed();
    getRootModel().assertWritable();
    for (ExcludeFolder excludeFolder : myExcludeFolders) {
      Disposer.dispose((Disposable)excludeFolder);
    }
    myExcludeFolders.clear();
  }

  @NotNull
  @Override
  public List<String> getExcludePatterns() {
    return myExcludePatterns != null ? Collections.unmodifiableList(myExcludePatterns) : Collections.emptyList();
  }

  @Override
  public void addExcludePattern(@NotNull String pattern) {
    if (myExcludePatterns == null) {
      myExcludePatterns = new SmartList<>();
    }
    myExcludePatterns.add(pattern);
  }

  @Override
  public void removeExcludePattern(@NotNull String pattern) {
    if (myExcludePatterns != null) {
      myExcludePatterns.remove(pattern);
      if (myExcludePatterns.isEmpty()) {
        myExcludePatterns = null;
      }
    }
  }

  @Override
  public void setExcludePatterns(@NotNull List<String> patterns) {
    if (patterns.isEmpty()) {
      myExcludePatterns = null;
    }
    else {
      if (myExcludePatterns == null) {
        myExcludePatterns = new SmartList<>();
      }
      else {
        myExcludePatterns.clear();
      }
      myExcludePatterns.addAll(patterns);
    }
  }

  private ExcludeFolder addExcludeFolder(ExcludeFolder f) {
    Disposer.register(this, (Disposable)f);
    myExcludeFolders.add(f);
    return f;
  }

  private <T extends ContentFolder> void assertCanRemoveFrom(T f, @NotNull Set<T> ff) {
    getRootModel().assertWritable();
    LOG.assertTrue(ff.contains(f));
  }

  private void assertFolderUnderMe(@NotNull String url) {
    final String path = VfsUtilCore.urlToPath(url);
    final String rootPath = VfsUtilCore.urlToPath(getUrl());
    if (!FileUtil.isAncestor(rootPath, path, false)) {
      LOG.error("The file '" + path + "' is not under content entry root '" + rootPath + "'");
    }
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  @NotNull
  public ContentEntry cloneEntry(@NotNull RootModelImpl rootModel) {
    assert !isDisposed();
    ContentEntryImpl cloned = new ContentEntryImpl(myRoot.getUrl(), rootModel);
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)sourceFolder).cloneFolder(cloned);
        cloned.addSourceFolder((SourceFolderImpl)folder);
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ClonableContentFolder) {
        ContentFolder folder = ((ClonableContentFolder)excludeFolder).cloneFolder(cloned);
        cloned.addExcludeFolder((ExcludeFolder)folder);
      }
    }

    for (String pattern : getExcludePatterns()) {
      cloned.addExcludePattern(pattern);
    }

    return cloned;
  }

  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    assert !isDisposed();
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
    element.setAttribute(URL_ATTRIBUTE, myRoot.getUrl());
    for (final SourceFolder sourceFolder : mySourceFolders) {
      if (sourceFolder instanceof SourceFolderImpl) {
        JpsModuleRootModelSerializer.saveSourceRoot(element, sourceFolder.getUrl(), sourceFolder.getJpsElement().asTyped());
      }
    }

    for (final ExcludeFolder excludeFolder : myExcludeFolders) {
      if (excludeFolder instanceof ExcludeFolderImpl) {
        final Element subElement = new Element(ExcludeFolderImpl.ELEMENT_NAME);
        ((ExcludeFolderImpl)excludeFolder).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (String pattern : getExcludePatterns()) {
      element.addContent(new Element(JpsModuleRootModelSerializer.EXCLUDE_PATTERN_TAG).setAttribute(JpsModuleRootModelSerializer.EXCLUDE_PATTERN_ATTRIBUTE, pattern));
    }
  }

  private static final class ContentFolderComparator implements Comparator<ContentFolder> {
    public static final ContentFolderComparator INSTANCE = new ContentFolderComparator();

    @Override
    public int compare(@NotNull ContentFolder o1, @NotNull ContentFolder o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }

  @Override
  public int compareTo(@NotNull ContentEntryImpl other) {
    int i = getUrl().compareTo(other.getUrl());
    if (i != 0) return i;
    i = ArrayUtil.lexicographicCompare(getSourceFolders(), other.getSourceFolders());
    if (i != 0) return i;
    i = ArrayUtil.lexicographicCompare(getExcludeFolders(), other.getExcludeFolders());
    if (i != 0) return i;
    return ContainerUtil.compareLexicographically(getExcludePatterns(), other.getExcludePatterns());
  }
}
