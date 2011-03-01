/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class RecursiveFileHolder<T> implements FileHolder {
  protected final HolderType myHolderType;
  protected final MembershipMapI<VirtualFile, T> myMap;

  protected final Project myProject;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myMap = new TreeMapAsMembershipMap<VirtualFile, T>(new PairProcessor<VirtualFile, VirtualFile>() {
      @Override
      public boolean process(final VirtualFile parent, final VirtualFile child) {
        return VfsUtil.isAncestor(parent, child, false);
      }
    }, FilePathComparator.getInstance());

    myProject = project;
    myHolderType = holderType;
  }

  public synchronized void cleanAll() {
    myMap.clear();
  }

  public void cleanAndAdjustScope(final VcsModifiableDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        // to avoid deadlocks caused by incorrect lock ordering, need to lock on this after taking read action
        synchronized (RecursiveFileHolder.this) {
          if (myProject.isDisposed()) return;

          final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myMap.keySet());
          for (final VirtualFile file : currentFiles) {
            if (isFileDirty(scope, file)) {
              myMap.remove(file);
            }
          }
        }
      }
    });
  }

  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    return fileDropped(file) || scope.belongsTo(new FilePathImpl(file));
  }

  public HolderType getType() {
    return myHolderType;
  }

  protected boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file) == null;
  }

  public synchronized void addFile(final VirtualFile file) {
    myMap.putOptimal(file, null);
  }

  public synchronized void removeFile(final VirtualFile file) {
    myMap.remove(file);
  }

  public synchronized RecursiveFileHolder copy() {
    final RecursiveFileHolder<T> copyHolder = new RecursiveFileHolder<T>(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    return copyHolder;
  }

  public synchronized boolean containsFile(final VirtualFile file) {
    return myMap.getMapping(file) != null;
  }

  public synchronized Collection<VirtualFile> values() {
    return myMap.keySet();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFileHolder that = (RecursiveFileHolder)o;

    if (!myMap.equals(that.myMap)) return false;

    return true;
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
