// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChangesPreprocess {
  private static final Logger LOG = Logger.getInstance(ChangesPreprocess.class);

  public static List<Change> preprocessChangesRemoveDeletedForDuplicateMoved(List<? extends Change> list) {
    final List<Change> result = new ArrayList<>();
    final Map<FilePath, Change> map = new HashMap<>();
    for (Change change : list) {
      if (change.getBeforeRevision() == null) {
        result.add(change);
      } else {
        final FilePath beforePath = ChangesUtil.getBeforePath(change);
        final Change existing = map.get(beforePath);
        if (existing == null) {
          map.put(beforePath, change);
          continue;
        }
        if (change.getAfterRevision() == null && existing.getAfterRevision() == null) continue;
        if (change.getAfterRevision() != null && existing.getAfterRevision() != null) {
          LOG.error("Incorrect changes list: " + list);
        }
        if (existing.getAfterRevision() != null && change.getAfterRevision() == null) {
          continue; // skip delete change
        }
        if (change.getAfterRevision() != null && existing.getAfterRevision() == null) {
          map.put(beforePath, change);  // skip delete change
        }
      }
    }
    result.addAll(map.values());
    return result;
  }
}
