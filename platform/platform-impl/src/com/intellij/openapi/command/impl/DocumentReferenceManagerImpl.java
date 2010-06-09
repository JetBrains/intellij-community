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
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.util.io.fs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocumentReferenceManagerImpl extends DocumentReferenceManager implements ApplicationComponent {
  private static final Key<List<VirtualFile>> DELETED_FILES = Key.create(DocumentReferenceManagerImpl.class.getName() + ".DELETED_FILES");

  private final Map<Reference<Document>, DocumentReference> myDocToRef = new WeakValueHashMap<Reference<Document>, DocumentReference>();
  private final Map<VirtualFile, DocumentReference> myFileToRef = new WeakValueHashMap<VirtualFile, DocumentReference>();
  private final Map<FilePath, DocumentReference> myDeletedFilePathToRef = new WeakValueHashMap<FilePath, DocumentReference>();

  @NotNull
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  public void initComponent() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(VirtualFileEvent event) {
        VirtualFile f = event.getFile();
        DocumentReference ref = myDeletedFilePathToRef.remove(new FilePath(f.getUrl()));
        if (ref != null) {
          myFileToRef.put(f, ref);
          ((DocumentReferenceByVirtualFile)ref).update(f);
        }
      }

      @Override
      public void beforeFileDeletion(VirtualFileEvent event) {
        VirtualFile f = event.getFile();
        f.putUserData(DELETED_FILES, collectDeletedFiles(f, new ArrayList<VirtualFile>()));
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        VirtualFile f = event.getFile();
        List<VirtualFile> files = f.getUserData(DELETED_FILES);
        f.putUserData(DELETED_FILES, null);

        for (VirtualFile each : files) {
          DocumentReference ref = myFileToRef.remove(each);
          if (ref != null) {
            myDeletedFilePathToRef.put(new FilePath(each.getUrl()), ref);
          }
        }
      }
    });
  }

  private List<VirtualFile> collectDeletedFiles(VirtualFile f, List<VirtualFile> files) {
    if (!(f instanceof NewVirtualFile)) return files;

    if (!f.isDirectory()) {
      files.add(f);
    }
    else {
      for (VirtualFile each : ((NewVirtualFile)f).iterInDbChildren()) {
        collectDeletedFiles(each, files);
      }
    }
    return files;
  }

  public void disposeComponent() {
  }

  @Override
  public DocumentReference create(@NotNull Document document) {
    assertInDispatchThread();

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file != null ? create(file) : doCreate(document);
  }

  private DocumentReference doCreate(@NotNull final Document document) {
    final int hashCode = document.hashCode();
    Reference<Document> reference = new WeakReference<Document>(document) {
      @Override
      public int hashCode() {
        return hashCode;
      }

      @Override
      public boolean equals(Object obj) {
        Document mydoc = get();
        return mydoc != null && obj instanceof Reference && ((Reference)obj).get() == mydoc;
      }
    };
    DocumentReference result = myDocToRef.get(reference);
    if (result == null) {
      result = new DocumentReferenceByDocument(document);
      myDocToRef.put(reference, result);
    }
    return result;
  }

  @Override
  public DocumentReference create(@NotNull VirtualFile file) {
    assertInDispatchThread();
    assert file.isValid() : "file is invalid: " + file;

    DocumentReference result = myFileToRef.get(file);
    if (result == null) {
      result = new DocumentReferenceByVirtualFile(file);
      myFileToRef.put(file, result);
    }
    return result;
  }

  private void assertInDispatchThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }
}
