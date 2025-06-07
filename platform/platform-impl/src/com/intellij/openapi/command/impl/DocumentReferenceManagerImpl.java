// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final Map<Document, DocumentReference> myDocToRef = ContainerUtil.createWeakKeyWeakValueMap();

  private static final Key<Reference<DocumentReference>> FILE_TO_REF_KEY = Key.create("FILE_TO_REF_KEY");
  private static final Key<DocumentReference> FILE_TO_STRONG_REF_KEY = Key.create("FILE_TO_STRONG_REF_KEY");
  private final Map<FilePath, DocumentReference> myDeletedFilePathToRef = ContainerUtil.createWeakValueMap();

  DocumentReferenceManagerImpl() {
    VirtualFileManager.getInstance().addAsyncFileListener(new AsyncFileListener() {
      @Override
      public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
        List<VirtualFile> deletedFiles = new NotNullList<>();
        for (VFileEvent event : events) {
          if (event instanceof VFileDeleteEvent) {
            collectDeletedFiles(((VFileDeleteEvent)event).getFile(), deletedFiles);
          }
        }

        return new ChangeApplier() {
          @Override
          public void afterVfsChange() {
            for (VirtualFile each : deletedFiles) {
              fileDeleted(each);
            }
            for (VFileEvent event : events) {
              if (event instanceof VFileCreateEvent) {
                fileCreated((VFileCreateEvent)event);
              }
            }
          }

        };
      }

      private void fileDeleted(VirtualFile each) {
        DocumentReference ref = SoftReference.dereference(each.getUserData(FILE_TO_REF_KEY));
        each.putUserData(FILE_TO_REF_KEY, null);
        if (ref != null) {
          myDeletedFilePathToRef.put(new FilePath(each.getUrl()), ref);
        }
      }

      private void fileCreated(@NotNull VFileCreateEvent event) {
        VirtualFile f = event.getFile();
        DocumentReference ref = f == null ? null : myDeletedFilePathToRef.remove(new FilePath(f.getUrl()));
        if (ref != null) {
          f.putUserData(FILE_TO_REF_KEY, new WeakReference<>(ref));
          ((DocumentReferenceByVirtualFile)ref).update(f);
        }
      }
    }, ApplicationManager.getApplication());
  }

  private static void collectDeletedFiles(@NotNull VirtualFile f, @NotNull List<? super VirtualFile> files) {
    if (!(f instanceof NewVirtualFile)) return;

    ProgressManager.checkCanceled();
    if (!f.isDirectory()) {
      files.add(f);
    }
    else {
      for (VirtualFile each : ((NewVirtualFile)f).iterInDbChildren()) {
        collectDeletedFiles(each, files);
      }
    }
  }

  @Override
  public @NotNull DocumentReference create(@NotNull Document document) {
    assertIsWriteThread();

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file == null || !file.isValid() ? createFromDocument(document) : create(file);
  }

  private @NotNull DocumentReference createFromDocument(final @NotNull Document document) {
    DocumentReference result = myDocToRef.get(document);
    if (result == null) {
      result = new DocumentReferenceByDocument(document);
      myDocToRef.put(document, result);
    }
    return result;
  }

  @Override
  public @NotNull DocumentReference create(@NotNull VirtualFile file) {
    assertIsWriteThread();

    if (!file.isInLocalFileSystem()) { // we treat local files differently from non local because we can undo their deletion
      DocumentReference reference = file.getUserData(FILE_TO_STRONG_REF_KEY);
      if (reference == null) {
        file.putUserData(FILE_TO_STRONG_REF_KEY, reference = new DocumentReferenceByNonlocalVirtualFile(file));
      }
      return reference;
    }

    assert file.isValid() : "file is invalid: " + file;

    DocumentReference result = SoftReference.dereference(file.getUserData(FILE_TO_REF_KEY));
    if (result == null) {
      result = new DocumentReferenceByVirtualFile(file);
      file.putUserData(FILE_TO_REF_KEY, new WeakReference<>(result));
    }
    return result;
  }

  private static void assertIsWriteThread() {
    ApplicationManager.getApplication().assertIsDispatchThread();
  }

  @TestOnly
  public void cleanupForNextTest() {
    myDeletedFilePathToRef.clear();
    myDocToRef.clear();
  }

}
