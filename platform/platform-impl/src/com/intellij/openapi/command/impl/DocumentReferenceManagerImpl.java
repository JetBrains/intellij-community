package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.WeakValueHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class DocumentReferenceManagerImpl extends DocumentReferenceManager implements ApplicationComponent {
  private final Map<Document, DocumentReference> myDocToRef = new WeakValueHashMap<Document, DocumentReference>();
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
      public void fileDeleted(VirtualFileEvent event) {
        VirtualFile f = event.getFile();
        DocumentReference ref = myFileToRef.remove(f);
        if (ref != null) {
          myDeletedFilePathToRef.put(new FilePath(f.getUrl()), ref);
        }
      }
    });
  }

  public void disposeComponent() {
  }

  @Override
  public DocumentReference create(@NotNull Document document) {
    assertInDispatchThread();

    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file != null ? create(file) : doCreate(document);
  }

  private DocumentReference doCreate(Document document) {
    DocumentReference result = myDocToRef.get(document);
    if (result == null) {
      result = new DocumentReferenceByDocument(document);
      myDocToRef.put(document, result);
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
