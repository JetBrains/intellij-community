package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IgnoredFilesComponent {
  private final Project myProject;
  private final Set<IgnoredFileBean> myFilesToIgnore;

  public IgnoredFilesComponent(final Project project) {
    myProject = project;
    myFilesToIgnore = new HashSet<IgnoredFileBean>();

    project.getMessageBus().connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(List<? extends VFileEvent> events) {}

      public void after(List<? extends VFileEvent> events) {
        resetCaches();
      }
    });
  }

  public void add(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public void clear() {
    synchronized (myFilesToIgnore) {
      myFilesToIgnore.clear();
    }
  }
  public boolean isEmpty() {
    synchronized (myFilesToIgnore) {
      return myFilesToIgnore.isEmpty();
    }
  }

  public void set(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      myFilesToIgnore.clear();
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    synchronized(myFilesToIgnore) {
      return myFilesToIgnore.toArray(new IgnoredFileBean[myFilesToIgnore.size()]);
    }
  }

  private void resetCaches() {
    synchronized (myFilesToIgnore) {
      for (IgnoredFileBean bean : myFilesToIgnore) {
        bean.resetCache();
      }
    }
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    synchronized(myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) return false;

      for(IgnoredFileBean bean: myFilesToIgnore) {
        if (bean.matchesFile(file)) return true;
      }
      return false;
    }
  }
}
