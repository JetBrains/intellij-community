/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/28/12
 * Time: 6:47 PM
 */
public class ChangesPreprocess {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesPreprocess");

  public static List<Change> preprocessChangesRemoveDeletedForDuplicateMoved(List<Change> list) {
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
