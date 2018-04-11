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

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 *  @author dsl
 */
public class SourceFolderImpl extends ContentFolderBaseImpl implements SourceFolder, ClonableContentFolder {
  private final JpsModuleSourceRoot myJpsElement;
  @NonNls public static final String ELEMENT_NAME = JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG;
  @NonNls public static final String TEST_SOURCE_ATTR = JpsModuleRootModelSerializer.IS_TEST_SOURCE_ATTRIBUTE;
  static final String DEFAULT_PACKAGE_PREFIX = "";

  SourceFolderImpl(@NotNull VirtualFile file, @NotNull JpsModuleSourceRoot jpsElement, @NotNull ContentEntryImpl contentEntry) {
    super(file, contentEntry);
    myJpsElement = jpsElement;
  }

  public SourceFolderImpl(@NotNull JpsModuleSourceRoot jpsElement, @NotNull ContentEntryImpl contentEntry) {
    super(jpsElement.getUrl(), contentEntry);
    myJpsElement = jpsElement;
  }

  SourceFolderImpl(Element element, ContentEntryImpl contentEntry) throws InvalidDataException {
    super(element, contentEntry);
    myJpsElement = JpsModuleRootModelSerializer.loadSourceRoot(element);
  }

  private SourceFolderImpl(SourceFolderImpl that, ContentEntryImpl contentEntry) {
    super(that, contentEntry);
    myJpsElement = createCopy(that, that.myJpsElement.asTyped());
  }

  private static <P extends JpsElement> JpsModuleSourceRoot createCopy(SourceFolderImpl that, final JpsTypedModuleSourceRoot<P> jpsElement) {
    return JpsElementFactory.getInstance().createModuleSourceRoot(that.getUrl(), jpsElement.getRootType(), (P)jpsElement.getProperties().getBulkModificationSupport().createCopy());
  }

  @Override
  public boolean isTestSource() {
    return getRootType().equals(JavaSourceRootType.TEST_SOURCE) || getRootType().equals(JavaResourceRootType.TEST_RESOURCE);
  }

  @NotNull
  @Override
  public String getPackagePrefix() {
    JavaSourceRootProperties properties = getJavaProperties();
    if (properties != null) {
      return properties.getPackagePrefix();
    }
    JavaResourceRootProperties resourceJavaProperties = getResourceJavaProperties();
    if (resourceJavaProperties != null) {
      return resourceJavaProperties.getRelativeOutputPath().replace('/', '.');
    }
    return DEFAULT_PACKAGE_PREFIX;
  }

  @Nullable
  private JavaSourceRootProperties getJavaProperties() {
    return myJpsElement.getProperties(JavaModuleSourceRootTypes.SOURCES);
  }

  @Nullable
  private JavaResourceRootProperties getResourceJavaProperties() {
    return myJpsElement.getProperties(JavaModuleSourceRootTypes.RESOURCES);
  }

  @Override
  public void setPackagePrefix(@NotNull String packagePrefix) {
    JavaSourceRootProperties properties = getJavaProperties();
    if (properties != null) {
      properties.setPackagePrefix(packagePrefix);
    }
  }

  @NotNull
  @Override
  public JpsModuleSourceRootType<?> getRootType() {
    return myJpsElement.getRootType();
  }

  @Override
  public ContentFolder cloneFolder(ContentEntry contentEntry) {
    assert !((ContentEntryImpl)contentEntry).isDisposed() : "target entry already disposed: " + contentEntry;
    assert !isDisposed() : "Already disposed: " + this;
    return new SourceFolderImpl(this, (ContentEntryImpl)contentEntry);
  }

  @NotNull
  public JpsModuleSourceRoot getJpsElement() {
    return myJpsElement;
  }

  private boolean isForGeneratedSources() {
    JavaSourceRootProperties properties = getJavaProperties();
    JavaResourceRootProperties resourceJavaProperties = getResourceJavaProperties();
    return properties != null && properties.isForGeneratedSources() || resourceJavaProperties != null && resourceJavaProperties.isForGeneratedSources();
  }

  @Override
  public int compareTo(ContentFolderBaseImpl folder) {
    if (!(folder instanceof SourceFolderImpl)) return -1;

    int i = super.compareTo(folder);
    if (i!= 0) return i;

    SourceFolderImpl sourceFolder = (SourceFolderImpl)folder;
    i = getPackagePrefix().compareTo(sourceFolder.getPackagePrefix());
    if (i!= 0) return i;
    i = Boolean.compare(isTestSource(), sourceFolder.isTestSource());
    if (i != 0) return i;
    i = Boolean.compare(isForGeneratedSources(), sourceFolder.isForGeneratedSources());
    if (i != 0) return i;
    //todo[nik] perhaps we should use LinkedSet instead of SortedSet and get rid of this method
    return myJpsElement.getRootType().getClass().getName().compareTo(sourceFolder.getRootType().getClass().getName());
  }
}
