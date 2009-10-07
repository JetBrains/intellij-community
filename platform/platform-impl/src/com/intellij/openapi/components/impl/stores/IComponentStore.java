package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface IComponentStore {
  @Nullable
  String initComponent(Object component, boolean service);
  void reinitComponents(Set<String> componentNames, boolean reloadData);
  boolean isReloadPossible(Set<String> componentNames);


  void load() throws IOException, StateStorage.StateStorageException;
  boolean isSaving();

  StateStorageManager getStateStorageManager();


  class SaveCancelledException extends IOException {

    public SaveCancelledException() {
    }

    public SaveCancelledException(final String s) {
      super(s);
    }
  }

  //todo:remove throws
  @NotNull
  SaveSession startSave() throws IOException;

  interface SaveSession {
    List<IFile> getAllStorageFilesToSave(final boolean includingSubStructures) throws IOException;
    SaveSession save() throws IOException;
    void finishSave();
    void reset();

    @Nullable
    Set<String> analyzeExternalChanges(Set<Pair<VirtualFile,StateStorage>> changedFiles);
    List<IFile> getAllStorageFiles(final boolean includingSubStructures);
  }

}
