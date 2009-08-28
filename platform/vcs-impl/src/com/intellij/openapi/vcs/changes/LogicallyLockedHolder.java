package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashMap;
import java.util.Map;

public class LogicallyLockedHolder implements FileHolder {
  private final Map<VirtualFile, LogicalLock> myMap;
  private final Project myProject;

  public LogicallyLockedHolder(final Project project) {
    myProject = project;
    myMap = new HashMap<VirtualFile, LogicalLock>();
  }

  public void cleanAll() {
    myMap.clear();
  }

  public void add(final VirtualFile file, final LogicalLock lock) {
    myMap.put(file, lock);
  }

  public void cleanScope(VcsDirtyScope scope) {
    VirtualFileHolder.cleanScope(myProject, myMap.keySet(), scope);
  }

  public FileHolder copy() {
    final LogicallyLockedHolder result = new LogicallyLockedHolder(myProject);
    result.myMap.putAll(myMap);
    return result;
  }

  public HolderType getType() {
    return HolderType.LOGICALLY_LOCKED;
  }

  public Map<VirtualFile, LogicalLock> getMap() {
    return myMap;
  }
}
