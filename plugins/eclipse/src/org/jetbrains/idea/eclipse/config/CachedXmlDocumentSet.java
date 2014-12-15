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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.storage.FileSet;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.EclipseJDOMUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CachedXmlDocumentSet implements FileSet {
  protected final Map<String, String> nameToDir = new THashMap<String, String>();
  protected final Map<String, Document> savedContent = new THashMap<String, Document>();
  protected final Map<String, Document> modifiedContent = new THashMap<String, Document>();
  protected final Set<String> deletedContent = new THashSet<String>();
  private final Project project;

  public CachedXmlDocumentSet(Project project) {
    this.project = project;
  }

  public Document read(@NotNull String name) throws IOException, JDOMException {
    return read(name, true);
  }

  public Document read(@NotNull String name, final boolean refresh) throws IOException, JDOMException {
    return load(name, refresh).clone();
  }

  public void write(Document document, String name) throws IOException {
    update(document.clone(), name);
  }

  public String getParent(@NotNull String name) {
    return nameToDir.get(name);
  }

  public void register(@NotNull String name, final String path) {
    nameToDir.put(name, path);
  }

  protected void assertKnownName(@NotNull String name) {
    assert nameToDir.containsKey(name) : name;
  }

  public boolean exists(String name) {
    assertKnownName(name);
    return !deletedContent.contains(name) && getVFile(name) != null;
  }

  @Nullable
  protected VirtualFile getVFile(@NotNull String name) {
    return getVFile(name, false);
  }

  @Nullable
  protected VirtualFile getVFile(@NotNull String name, boolean refresh) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(getParent(name), name));
    if (file != null && refresh) {
      file.refresh(false, true);
      if (!file.isValid()) return null;
    }
    return file;
  }

  @NotNull
  private VirtualFile getOrCreateVFile(@NotNull final String name) throws IOException {
    final VirtualFile vFile = getVFile(name);
    if (vFile != null) {
      return vFile;
    }
    else {
      final VirtualFile vDir = LocalFileSystem.getInstance().findFileByIoFile(new File(getParent(name)));
      if (vDir == null) {
        throw new IOException(name + ": file not found");
      }
      final IOException[] ex = new IOException[1];
      final VirtualFile file = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Override
        public VirtualFile compute() {
          try {
            return vDir.createChildData(this, name);
          }
          catch (IOException e) {
            ex[0] = e;
            return null;
          }
        }
      });
      if (ex[0] != null) throw ex[0];
      return file;
    }
  }

  protected Document load(@NotNull String name, boolean refresh) throws IOException, JDOMException {
    assertKnownName(name);
    final Document logical = modifiedContent.get(name);
    if (logical != null) {
      return logical;
    }

    Document physical = savedContent.get(name);
    if (physical == null) {
      final VirtualFile vFile = deletedContent.contains(name) ? null : getVFile(name, refresh);
      if (vFile == null) {
        throw new IOException(name + ": file does not exist");
      }
      final InputStream is = vFile.getInputStream();
      try {
        physical = JDOMUtil.loadDocument(is);
      }
      finally {
        is.close();
      }
      savedContent.put(name, physical);
    }
    return physical;

  }

  public void preload() {
    for (String key : nameToDir.keySet()) {
      try {
        load(key, false);
      }
      catch (IOException ignore) {
      }
      catch (JDOMException ignore) {
      }
    }
  }

  public void update(Document content, final String name) {
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

  @Override
  public void listFiles(@NotNull List<VirtualFile> list) {
    Set<String> existingFiles = new THashSet<String>(savedContent.keySet());
    existingFiles.addAll(modifiedContent.keySet());
    for (String key : existingFiles) {
      try {
        if (getVFile(key) == null) {
          // deleted on disk
          savedContent.remove(key);
        }
        list.add(getOrCreateVFile(key));
      }
      catch (IOException ignore) {
      }
    }
    Set<String> newFiles = new THashSet<String>(nameToDir.keySet());
    newFiles.removeAll(existingFiles);
    for (String name : newFiles) {
      VirtualFile vFile = getVFile(name);
      if (vFile != null) {
        list.add(vFile);
      }
    }
  }

  @Override
  public boolean hasChanged() {
    for (String key : modifiedContent.keySet()) {
      if (hasChanged(key)) {
        return true;
      }
    }
    return !deletedContent.isEmpty();
  }

  private boolean hasChanged(final String key) {
    final Document content = modifiedContent.get(key);
    final Document physical1 = content == null ? null : content;
    final Document physical2 = savedContent.get(key);
    if (physical1 != null && physical2 != null) {
      return !JDOMUtil.areDocumentsEqual(physical1, physical2);
    }
    return physical1 != physical2;
  }

  @Override
  public void commit() throws IOException {
    for (String key : modifiedContent.keySet()) {
      if (hasChanged(key)) {
        final Document content = modifiedContent.get(key);
        if (content != null) {
          final Writer writer = new OutputStreamWriter(getOrCreateVFile(key).getOutputStream(this), "UTF-8");
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
          VirtualFile file = getVFile(deleted);
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
