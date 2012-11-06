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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 *  @author dsl
 */
public class SourceFolderImpl extends ContentFolderBaseImpl implements SourceFolder, ClonableContentFolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleSourceFolderImpl");
  private final boolean myIsTestSource;
  @NonNls public static final String ELEMENT_NAME = JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG;
  @NonNls public static final String TEST_SOURCE_ATTR = JpsModuleRootModelSerializer.IS_TEST_SOURCE_ATTRIBUTE;
  private String myPackagePrefix;
  static final String DEFAULT_PACKAGE_PREFIX = "";

  SourceFolderImpl(@NotNull VirtualFile file, boolean isTestSource, @NotNull ContentEntryImpl contentEntry) {
    this(file, isTestSource, DEFAULT_PACKAGE_PREFIX, contentEntry);
  }

  SourceFolderImpl(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix, @NotNull ContentEntryImpl contentEntry) {
    super(file, contentEntry);
    myIsTestSource = isTestSource;
    myPackagePrefix = packagePrefix;
  }

  public SourceFolderImpl(@NotNull String url, boolean isTestSource, @NotNull ContentEntryImpl contentEntry) {
    super(url, contentEntry);
    myIsTestSource = isTestSource;
    myPackagePrefix = DEFAULT_PACKAGE_PREFIX;
  }

  SourceFolderImpl(Element element, ContentEntryImpl contentEntry) throws InvalidDataException {
    super(element, contentEntry);
    LOG.assertTrue(element.getName().equals(ELEMENT_NAME));
    final String testSource = element.getAttributeValue(TEST_SOURCE_ATTR);
    if (testSource == null) throw new InvalidDataException();
    myIsTestSource = Boolean.valueOf(testSource).booleanValue();
    final String packagePrefix = element.getAttributeValue(JpsModuleRootModelSerializer.PACKAGE_PREFIX_ATTRIBUTE);
    if (packagePrefix != null) {
      myPackagePrefix = packagePrefix;
    }
    else {
      myPackagePrefix = DEFAULT_PACKAGE_PREFIX;
    }
  }

  private SourceFolderImpl(SourceFolderImpl that, ContentEntryImpl contentEntry) {
    super(that, contentEntry);
    myIsTestSource = that.myIsTestSource;
    myPackagePrefix = that.myPackagePrefix;
  }

  @Override
  public boolean isTestSource() {
    return myIsTestSource;
  }

  @NotNull
  @Override
  public String getPackagePrefix() {
    return myPackagePrefix;
  }

  @Override
  public void setPackagePrefix(@NotNull String packagePrefix) {
    myPackagePrefix = packagePrefix;
  }

  void writeExternal(Element element) {
    writeFolder(element, ELEMENT_NAME);
    element.setAttribute(TEST_SOURCE_ATTR, Boolean.toString(myIsTestSource));
    if (!DEFAULT_PACKAGE_PREFIX.equals(myPackagePrefix)) {
      element.setAttribute(JpsModuleRootModelSerializer.PACKAGE_PREFIX_ATTRIBUTE, myPackagePrefix);
    }
  }

  @Override
  public ContentFolder cloneFolder(ContentEntry contentEntry) {
    assert !((ContentEntryImpl)contentEntry).isDisposed() : "target entry already disposed: " + contentEntry;
    assert !isDisposed() : "Already disposed: " + this;
    return new SourceFolderImpl(this, (ContentEntryImpl)contentEntry);
  }

  @Override
  public int compareTo(ContentFolderBaseImpl folder) {
    if (!(folder instanceof SourceFolderImpl)) return -1;

    int i = super.compareTo(folder);
    if (i!= 0) return i;

    i = myPackagePrefix.compareTo(((SourceFolderImpl)folder).myPackagePrefix);
    if (i!= 0) return i;
    return Boolean.valueOf(myIsTestSource).compareTo(((SourceFolderImpl)folder).myIsTestSource);
  }
}
