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
import com.intellij.openapi.roots.UserDefinedExcludeFolder;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 *  @author dsl
 */
public class ExcludeFolderImpl extends ContentFolderBaseImpl implements ClonableContentFolder,
                                                                        UserDefinedExcludeFolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.SimpleExcludeFolderImpl");
  @NonNls public static final String ELEMENT_NAME = JpsModuleRootModelSerializer.EXCLUDE_FOLDER_TAG;

  ExcludeFolderImpl(@NotNull VirtualFile file, @NotNull ContentEntryImpl contentEntry) {
    super(file, contentEntry);
  }

  ExcludeFolderImpl(@NotNull String url, @NotNull ContentEntryImpl contentEntry) {
    super(url, contentEntry);
  }

  ExcludeFolderImpl(@NotNull Element element, @NotNull ContentEntryImpl contentEntry) throws InvalidDataException {
    super(element, contentEntry);
    LOG.assertTrue(ELEMENT_NAME.equals(element.getName()));
  }

  private ExcludeFolderImpl(@NotNull ExcludeFolderImpl that, @NotNull ContentEntryImpl contentEntry) {
    super(that, contentEntry);
  }

  public void writeExternal(Element element) {
    writeFolder(element, ELEMENT_NAME);
  }

  @Override
  public ContentFolder cloneFolder(ContentEntry contentEntry) {
    return new ExcludeFolderImpl(this, (ContentEntryImpl)contentEntry);
  }

  @Override
  public int compareTo(ContentFolderBaseImpl folder) {
    if (!(folder instanceof ExcludeFolderImpl)) return -1;
    return super.compareTo(folder);
  }
}
