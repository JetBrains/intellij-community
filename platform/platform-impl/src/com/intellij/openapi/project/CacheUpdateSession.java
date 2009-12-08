/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;

import java.util.*;

public class CacheUpdateSession {
  private static final Logger LOG = Logger.getInstance("#" + CacheUpdateSession.class.getName());
  private static final Key<Boolean> FAILED_TO_INDEX = Key.create(CacheUpdateSession.class.getSimpleName() + ".FAILED_TO_INDEX");
  private final Collection<VirtualFile> myFilesToUpdate;
  private final List<Pair<CacheUpdater, Collection<VirtualFile>>> myUpdatersWithFiles =
    new ArrayList<Pair<CacheUpdater, Collection<VirtualFile>>>();

  public CacheUpdateSession(Collection<CacheUpdater> updaters, ProgressIndicator indicator) {
    List<CacheUpdater> processedUpdaters = new ArrayList<CacheUpdater>();

    myFilesToUpdate = new LinkedHashSet<VirtualFile>();
    try {
      for (CacheUpdater each : updaters) {
        indicator.checkCanceled();
        try {
          List<VirtualFile> updaterFiles = Arrays.asList(each.queryNeededFiles());
          processedUpdaters.add(each);
          myFilesToUpdate.addAll(updaterFiles);
          myUpdatersWithFiles.add(Pair.create(each, (Collection<VirtualFile>)new THashSet<VirtualFile>(updaterFiles)));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    }
    catch (ProcessCanceledException e) {
      for (CacheUpdater each : processedUpdaters) {
        each.canceled();
      }
      throw e;
    }
  }

  public Collection<VirtualFile> getFilesToUpdate() {
    return myFilesToUpdate;
  }

  public void processFile(FileContent content) {
    VirtualFile file = content.getVirtualFile();
    boolean isValid = file.isValid();

    Iterator<Pair<CacheUpdater, Collection<VirtualFile>>> it = myUpdatersWithFiles.iterator();
    while (it.hasNext()) {
      Pair<CacheUpdater, Collection<VirtualFile>> eachPair = it.next();
      CacheUpdater eachUpdater = eachPair.first;
      Collection<VirtualFile> eachFiles = eachPair.second;

      if (!eachFiles.contains(file)) continue;

      try {
        if (isValid && !Boolean.TRUE.equals(file.getUserData(FAILED_TO_INDEX))) {
          eachUpdater.processFile(content);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error("Error while indexing " + file.getPresentableUrl() + "\n" + "To reindex this file IDEA has to be restarted", e);
        file.putUserData(FAILED_TO_INDEX, Boolean.TRUE);
      }
      eachFiles.remove(file);

      if (eachFiles.isEmpty()) {
        try {
          eachUpdater.updatingDone();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        it.remove();
      }
    }
  }

  public void updatingDone() {
    for (Pair<CacheUpdater, Collection<VirtualFile>> eachPair : myUpdatersWithFiles) {
      try {
        CacheUpdater eachUpdater = eachPair.first;
        eachUpdater.updatingDone();
        if (!eachPair.second.isEmpty()) {
          LOG.error(CacheUpdater.class.getSimpleName() + " " + eachUpdater + " has not finished yet:\n" + eachPair.second);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  public void canceled() {
    for (Pair<CacheUpdater, Collection<VirtualFile>> eachPair : myUpdatersWithFiles) {
      eachPair.first.canceled();
    }
  }
}
