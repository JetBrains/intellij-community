package com.intellij.openapi.vcs.changes.conflicts;

import com.intellij.openapi.vcs.readOnlyHandler.HandleTypeFactory;
import com.intellij.openapi.vcs.readOnlyHandler.HandleType;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ChangelistConflictHandleTypeFactory implements HandleTypeFactory {

  private final ChangelistConflictTracker myConflictTracker;

  public ChangelistConflictHandleTypeFactory(ChangeListManagerImpl manager) {
    myConflictTracker = manager.getConflictTracker();
  }

  public HandleType createHandleType(VirtualFile file) {
    if (myConflictTracker.hasConflict(file)) {
      return new HandleType("using conflict tracker", false) {
        @Override
        public void processFiles(Collection<VirtualFile> virtualFiles) {

        }
      };
    }
    return null;
  }
}
