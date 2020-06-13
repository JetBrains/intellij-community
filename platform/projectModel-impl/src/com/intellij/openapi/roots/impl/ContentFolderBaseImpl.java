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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 *  @author dsl
 */
@ApiStatus.Internal
abstract class ContentFolderBaseImpl extends RootModelComponentBase implements ContentFolder, Comparable<ContentFolderBaseImpl> {
  @NonNls public static final String URL_ATTRIBUTE = JpsModuleRootModelSerializer.URL_ATTRIBUTE;

  private static final Logger LOG = Logger.getInstance(ContentFolderBaseImpl.class);

  private final VirtualFilePointer myFilePointer;
  protected final ContentEntryImpl myContentEntry;

  ContentFolderBaseImpl(@NotNull VirtualFile file, @NotNull ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer = VirtualFilePointerManager.getInstance().create(file, this, getRootModel().getRootsChangedListener());
  }

  ContentFolderBaseImpl(@NotNull String url, @NotNull ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer = VirtualFilePointerManager.getInstance().create(url, this, getRootModel().getRootsChangedListener());
  }

  protected ContentFolderBaseImpl(@NotNull ContentFolderBaseImpl that, @NotNull ContentEntryImpl contentEntry) {
    this(that.myFilePointer, contentEntry);
  }

  ContentFolderBaseImpl(@NotNull Element element, @NotNull ContentEntryImpl contentEntry) throws InvalidDataException {
    this(getUrlFrom(element), contentEntry);
  }

  protected ContentFolderBaseImpl(@NotNull VirtualFilePointer filePointer, @NotNull ContentEntryImpl contentEntry) {
    super(contentEntry.getRootModel());
    myContentEntry = contentEntry;
    myFilePointer = VirtualFilePointerManager.getInstance().duplicate(filePointer,this, getRootModel().getRootsChangedListener());
  }

  @NotNull
  private static String getUrlFrom(@NotNull Element element) throws InvalidDataException {
    String url = element.getAttributeValue(URL_ATTRIBUTE);
    if (url == null) throw new InvalidDataException();
    return url;
  }

  @Override
  public VirtualFile getFile() {
    return myFilePointer.getFile();
  }

  @Override
  @NotNull
  public ContentEntry getContentEntry() {
    return myContentEntry;
  }

  protected void writeFolder(@NotNull Element element, @NotNull String elementName) {
    LOG.assertTrue(element.getName().equals(elementName));
    element.setAttribute(URL_ATTRIBUTE, myFilePointer.getUrl());
  }

  @Override
  @NotNull
  public String getUrl() {
    return myFilePointer.getUrl();
  }

  @Override
  public boolean isSynthetic() {
    return false;
  }

  @Override
  public int compareTo(ContentFolderBaseImpl folder) {
    return getUrl().compareTo(folder.getUrl());
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ContentFolderBaseImpl)) return false;
    return compareTo((ContentFolderBaseImpl)obj) == 0;
  }

  @Override
  public int hashCode() {
    return getUrl().hashCode();
  }
  
  @Nullable
  @Override
  public String toString() {
    return myFilePointer == null ? null : getUrl();
  }
}
