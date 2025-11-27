// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.NotNullList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;


public final class DocumentReferenceManagerImpl extends DocumentReferenceManager {
  private static final Key<Reference<DocumentReference>> FILE_TO_REF_KEY = Key.create("FILE_TO_REF_KEY");
  private static final Key<DocumentReference> FILE_TO_STRONG_REF_KEY = Key.create("FILE_TO_STRONG_REF_KEY");

  private final Map<Document, DocumentReference> docToRef = ContainerUtil.createWeakKeyWeakValueMap();
  private final Map<FilePath, DocumentReference> deletedFilePathToRef = ContainerUtil.createWeakValueMap();

  DocumentReferenceManagerImpl() {
    VirtualFileManager.getInstance().addAsyncFileListener(new CreateDeleteFileListener(), ApplicationManager.getApplication());
  }

  @Override
  public @NotNull DocumentReference create(@NotNull Document document) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return (file != null && file.isValid()) ? create(file) : createFromDocument(document);
  }

  @Override
  public @NotNull DocumentReference create(@NotNull VirtualFile file) {
    // we treat local files differently from non-local because we can undo their deletion
    return file.isInLocalFileSystem() ? createFromLocal(file) : createFromNonLocal(file);
  }

  private synchronized @NotNull DocumentReference createFromDocument(@NotNull Document document) {
    DocumentReference ref = docToRef.get(document);
    if (ref != null) {
      return ref;
    }
    var newRef = new DocumentReferenceByDocument(document);
    docToRef.put(document, newRef);
    return newRef;
  }

  private synchronized @NotNull DocumentReference createFromLocal(@NotNull VirtualFile file) {
    assert file.isValid() : "file is invalid: " + file;
    DocumentReference ref = SoftReference.dereference(file.getUserData(FILE_TO_REF_KEY));
    if (ref != null) {
      return ref;
    }
    var newRef = new DocumentReferenceByVirtualFile(file);
    file.putUserData(FILE_TO_REF_KEY, new WeakReference<>(newRef));
    return newRef;
  }

  private synchronized @NotNull DocumentReference createFromNonLocal(@NotNull VirtualFile file) {
    DocumentReference ref = file.getUserData(FILE_TO_STRONG_REF_KEY);
    if (ref != null) {
      return ref;
    }
    var newRef = new DocumentReferenceByNonlocalVirtualFile(file);
    file.putUserData(FILE_TO_STRONG_REF_KEY, newRef);
    return newRef;
  }

  private synchronized void fileCreated(@NotNull VFileCreateEvent event) {
    VirtualFile file = event.getFile();
    if (file != null) {
      DocumentReference ref = deletedFilePathToRef.remove(filePath(file));
      if (ref != null) {
        file.putUserData(FILE_TO_REF_KEY, new WeakReference<>(ref));
        ((DocumentReferenceByVirtualFile) ref).update(file);
      }
    }
  }

  private synchronized void fileDeleted(@NotNull VirtualFile file) {
    DocumentReference ref = SoftReference.dereference(file.getUserData(FILE_TO_REF_KEY));
    file.putUserData(FILE_TO_REF_KEY, null);
    if (ref != null) {
      deletedFilePathToRef.put(filePath(file), ref);
    }
  }

  private final class CreateDeleteFileListener implements AsyncFileListener {

    @Override
    public @NotNull ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
      List<VirtualFile> deletedFiles = new NotNullList<>();
      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent deleteEvent) {
          collectDeletedFiles(deleteEvent.getFile(), deletedFiles);
        }
      }
      return new ChangeApplier() {
        @Override
        public void afterVfsChange() {
          for (VirtualFile file : deletedFiles) {
            fileDeleted(file);
          }
          for (VFileEvent event : events) {
            if (event instanceof VFileCreateEvent createEvent) {
              fileCreated(createEvent);
            }
          }
        }
      };
    }

    private static void collectDeletedFiles(@NotNull VirtualFile parentFile, @NotNull List<? super VirtualFile> collectedFiles) {
      if (parentFile instanceof NewVirtualFile file) {
        ProgressManager.checkCanceled();
        if (parentFile.isDirectory()) {
          for (VirtualFile childFile : file.iterInDbChildren()) {
            collectDeletedFiles(childFile, collectedFiles);
          }
        } else {
          collectedFiles.add(parentFile);
        }
      }
    }
  }

  private static @NotNull FilePath filePath(@NotNull VirtualFile file) {
    return new FilePath(file.getUrl());
  }

  @TestOnly
  public synchronized void cleanupForNextTest() {
    docToRef.clear();
    deletedFilePathToRef.clear();
  }
}
