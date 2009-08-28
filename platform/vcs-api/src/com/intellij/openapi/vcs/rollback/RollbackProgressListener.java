package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.List;

/**
 * {@link RollbackEnvironment} implementation should notify about starting change processing
 * using this interface; change processing can be reported using any <code>accept()</code> method signature,
 * should be reported once per change
 */
public interface RollbackProgressListener {
  RollbackProgressListener EMPTY = new RollbackProgressListener() {
    public void accept(final Change change) {
    }
    public void accept(final FilePath filePath) {
    }
    public void accept(final List<FilePath> paths) {
    }
    public void accept(final File file) {
    }
    public void accept(final VirtualFile file) {
    }
    public void checkCanceled() {
    }
    public void indeterminate() {
    }
    public void determinate() {
    }
  };

  void determinate();
  void indeterminate();
  void accept(final Change change);
  void accept(final FilePath filePath);
  void accept(final List<FilePath> paths);
  void accept(final File file);
  void accept(final VirtualFile file);
  void checkCanceled();
}
