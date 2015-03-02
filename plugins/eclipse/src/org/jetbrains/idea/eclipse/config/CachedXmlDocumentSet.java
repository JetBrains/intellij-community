/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.FileSet;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CachedXmlDocumentSet implements FileSet {
  protected final Map<String, String> nameToDir = new THashMap<String, String>();
  protected final Map<String, Element> savedContent = new THashMap<String, Element>();
  protected final Map<String, Element> modifiedContent = new THashMap<String, Element>();
  protected final Set<String> deletedContent = new THashSet<String>();
  private final Project project;

  public CachedXmlDocumentSet(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public Element read(@NotNull String name) throws IOException, JDOMException {
    return read(name, true);
  }

  @NotNull
  public Element read(@NotNull String name, final boolean refresh) throws IOException, JDOMException {
    return load(name, refresh).clone();
  }

  public void write(Element element, String name) throws IOException {
    update(element.clone(), name);
  }

  public String getParent(@NotNull String name) {
    return nameToDir.get(name);
  }

  public void register(@NotNull String name, @NotNull String path) {
    nameToDir.put(name, path);
  }

  protected void assertKnownName(@NotNull String name) {
    assert nameToDir.containsKey(name) : name;
  }

  public boolean exists(String name) {
    assertKnownName(name);
    return !deletedContent.contains(name) && getFile(name) != null;
  }

  @Nullable
  protected VirtualFile getFile(@NotNull String name) {
    return getFile(name, false);
  }

  @Nullable
  protected VirtualFile getFile(@NotNull String name, boolean refresh) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(getParent(name), name));
    if (file != null && refresh) {
      file.refresh(false, true);
      if (!file.isValid()) {
        return null;
      }
    }
    return file;
  }

  @NotNull
  private VirtualFile getOrCreateVFile(@NotNull String name) throws IOException {
    return StorageUtil.getOrCreateVirtualFile(this, new File(getParent(name), name));
  }

  @Nullable
  protected Element load(@NotNull String name, boolean refresh) throws IOException, JDOMException {
    assertKnownName(name);

    Element logical = modifiedContent.get(name);
    if (logical != null) {
      return logical;
    }

    Element physical = savedContent.get(name);
    if (physical == null) {
      VirtualFile file = deletedContent.contains(name) ? null : getFile(name, refresh);
      if (file == null) {
        return null;
      }

      InputStream inputStream = file.getInputStream();
      try {
        physical = JDOMUtil.load(inputStream);
      }
      finally {
        inputStream.close();
      }
      savedContent.put(name, physical);
    }
    return physical;
  }

  public void update(Element content, final String name) {
    assertKnownName(name);
    modifiedContent.put(name, content);
    deletedContent.remove(name);
  }

  public void delete(String name) {
    modifiedContent.remove(name);
    savedContent.remove(name);
    deletedContent.add(name);
    //nameToDir.remove(name);
  }

  @NotNull
  @Override
  public List<String> getFileUrls() {
    List<String> list = new ArrayList<String>(nameToDir.size());
    for (String name : nameToDir.keySet()) {
      list.add(StandardFileSystems.FILE_PROTOCOL_PREFIX + getParent(name) + '/' + name);
    }
    return list;
  }

  public boolean hasChanged() {
    for (String key : modifiedContent.keySet()) {
      if (hasChanged(key)) {
        return true;
      }
    }
    return !deletedContent.isEmpty();
  }

  private boolean hasChanged(@NotNull String key) {
    Element content = modifiedContent.get(key);
    Element physical1 = content == null ? null : content;
    Element physical2 = savedContent.get(key);
    if (physical1 != null && physical2 != null) {
      return !JDOMUtil.areElementsEqual(physical1, physical2);
    }
    return physical1 != physical2;
  }

  public void commit() throws IOException {
    for (String key : modifiedContent.keySet()) {
      if (hasChanged(key)) {
        Element content = modifiedContent.get(key);
        if (content != null) {
          Writer writer = new OutputStreamWriter(getOrCreateVFile(key).getOutputStream(this), CharsetToolkit.UTF8_CHARSET);
          try {
            EclipseJDOMUtil.output(content, writer, project);
          }
          finally {
            writer.close();
          }
          savedContent.put(key, content);
        }
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (String deleted : deletedContent) {
          VirtualFile file = getFile(deleted);
          if (file != null) {
            try {
              file.delete(this);
            }
            catch (IOException ignore) {
            }
          }
        }
        deletedContent.clear();
      }
    });
  }


}
