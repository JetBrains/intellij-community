/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelectFilesToAddTextsToPatchPanel {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.patch.SelectFilesToAddTextsToPatchPanel");

  public static Set<Change> getBig(List<Change> changes) {
    final Set<Change> exclude = new HashSet<Change>();
    for (Change change : changes) {
      ContentRevision beforeRevision = change.getBeforeRevision();
      if (beforeRevision != null) {
        try {
          String content = beforeRevision.getContent();
          if (content == null) {
            final FilePath file = beforeRevision.getFile();
            LOG.info("null content for " + (file.getPath()) + ", is dir: " + (file.isDirectory()));
            continue;
          }
          if (content.length() > VcsConfiguration.ourMaximumFileForBaseRevisionSize) {
            exclude.add(change);
          }
        }
        catch (VcsException e) {
          LOG.info(e);
        }
      }
    }
    return exclude;
  }
}
