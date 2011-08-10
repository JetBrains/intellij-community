package com.intellij.mock;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoListener;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.util.containers.WeakFactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;

public class MockFileDocumentManagerImpl extends FileDocumentManager {
  private static final Key<VirtualFile> MOCK_VIRTUAL_FILE_KEY = Key.create("MockVirtualFile");

  public VirtualFile myFile;
  public FileViewProvider myViewProvider;
  private final WeakFactoryMap<VirtualFile,Document> myDocuments = new WeakFactoryMap<VirtualFile, Document>() {
    @Override
    protected Document create(final VirtualFile key) {
      CharSequence text = LoadTextUtil.loadText(key);
      final Document document = EditorFactory.getInstance().createDocument(text);
      document.putUserData(MOCK_VIRTUAL_FILE_KEY, key);
      return document;
    }
  };

  @Override
  public Document getDocument(@NotNull VirtualFile file) {
    return myDocuments.get(file);
  }

  @Override
  public Document getCachedDocument(@NotNull VirtualFile file) {
    Reference<Document> reference = file.getUserData(FileDocumentManagerImpl.DOCUMENT_KEY);
    return reference != null ? reference.get() : null;
  }

  @Override
  public VirtualFile getFile(@NotNull Document document) {
    return document.getUserData(MOCK_VIRTUAL_FILE_KEY);
  }

  @Override
  public void saveAllDocuments() {
  }

  @Override
  public void saveDocument(@NotNull Document document) {
  }

  @Override
  @NotNull
  public Document[] getUnsavedDocuments() {
    return new Document[0];
  }

  @Override
  public boolean isDocumentUnsaved(@NotNull Document document) {
    return false;
  }

  @Override
  public boolean isFileModified(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public void addFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer) {
  }

  @Override
  public void removeFileDocumentSynchronizationVetoer(@NotNull FileDocumentSynchronizationVetoListener vetoer) {
  }

  @Override
  public void addFileDocumentManagerListener(@NotNull FileDocumentManagerListener listener) {
  }

  @Override
  public void removeFileDocumentManagerListener(@NotNull FileDocumentManagerListener listener) {
  }

  @Override
  public void reloadFromDisk(@NotNull Document document) {
  }

  @Override
  public void reloadFiles(final VirtualFile... files) {
  }

  @Override
  @NotNull
  public String getLineSeparator(VirtualFile file, Project project) {
    return "";
  }

  @Override
  public boolean requestWriting(@NotNull Document document, @Nullable Project project) {
    return true;
  }
}
