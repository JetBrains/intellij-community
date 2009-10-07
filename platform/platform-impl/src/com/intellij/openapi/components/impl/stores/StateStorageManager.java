package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.*;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author mike
 */
public interface StateStorageManager {
  void addMacro(String macro, String expansion);
  @Nullable
  TrackingPathMacroSubstitutor getMacroSubstitutor();

  @Nullable
  StateStorage getStateStorage(@NotNull Storage storageSpec) throws StateStorage.StateStorageException;

  @Nullable
  StateStorage getFileStateStorage(String fileName);

  Collection<String> getStorageFileNames();

  void clearStateStorage(@NotNull String file);

  ExternalizationSession startExternalization();
  SaveSession startSave(ExternalizationSession externalizationSession) ;
  void finishSave(SaveSession saveSession);

  @Nullable
  StateStorage getOldStorage(Object component, final String componentName, final StateStorageOperation operation) throws StateStorage.StateStorageException;

  @Nullable
  String expandMacroses(String file);

  void registerStreamProvider(StreamProvider streamProvider, final RoamingType type);

  void unregisterStreamProvider(StreamProvider streamProvider, final RoamingType roamingType);

  StreamProvider[] getStreamProviders(final RoamingType roamingType);

  void reset();


  interface ExternalizationSession {
    void setState(@NotNull Storage[] storageSpecs, Object component, final String componentName, Object state) throws StateStorage.StateStorageException;
    void setStateInOldStorage(Object component, final String componentName, Object state) throws StateStorage.StateStorageException;
  }

  interface SaveSession {
    //returns set of component which were changed, null if changes are much more than just component state.
    @Nullable
    Set<String> analyzeExternalChanges(Set<Pair<VirtualFile, StateStorage>> files);

    List<IFile> getAllStorageFilesToSave() throws StateStorage.StateStorageException;
    List<IFile> getAllStorageFiles();
    void save() throws StateStorage.StateStorageException;
  }
}
