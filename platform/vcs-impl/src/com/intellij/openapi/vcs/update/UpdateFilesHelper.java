// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.Couple;
import com.intellij.util.Consumer;

import java.util.List;

public final class UpdateFilesHelper {
  private UpdateFilesHelper() {
  }

  public static void iterateFileGroupFilesDeletedOnServerFirst(final UpdatedFiles updatedFiles, final Callback callback) {
    final FileGroup changedOnServer = updatedFiles.getGroupById(FileGroup.CHANGED_ON_SERVER_ID);
    if (changedOnServer != null) {
      final List<FileGroup> children = changedOnServer.getChildren();
      for (FileGroup child : children) {
        if (FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(child.getId())) {
          iterateGroup(child, callback);
        }
      }
    }

    final List<FileGroup> groups = updatedFiles.getTopLevelGroups();
    for (FileGroup group : groups) {
      iterateGroup(group, callback);

      for (FileGroup childGroup : group.getChildren()) {
        if (! FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(childGroup.getId())) {
          iterateGroup(childGroup, callback);
        }
      }
    }
  }

  private static void iterateGroup(final FileGroup group, final Callback callback) {
    for (String file : group.getFiles()) {
      callback.onFile(file, group.getId());
    }
  }

  public static void iterateFileGroupFiles(final UpdatedFiles updatedFiles, final Callback callback) {
    final List<FileGroup> groups = updatedFiles.getTopLevelGroups();
    for (FileGroup group : groups) {
      iterateGroup(group, callback);

      // for changed on server
      for (FileGroup childGroup : group.getChildren()) {
        iterateGroup(childGroup, callback);
      }
    }
  }

  private static void iterateGroup(final FileGroup group, final Consumer<? super Couple<String>> callback) {
    for (FileGroup.UpdatedFile updatedFile : group.getUpdatedFiles()) {
      callback.consume(Couple.of(updatedFile.getPath(), updatedFile.getVcsName()));
    }
  }

  public static void iterateAffectedFiles(final UpdatedFiles updatedFiles, final Consumer<? super Couple<String>> callback) {
    final List<FileGroup> groups = updatedFiles.getTopLevelGroups();
    for (FileGroup group : groups) {
      iterateGroup(group, callback);

      // for changed on server
      for (FileGroup childGroup : group.getChildren()) {
        iterateGroup(childGroup, callback);
      }
    }
  }

  public interface Callback {
    void onFile(final String filePath, final String groupId);
  }
}
