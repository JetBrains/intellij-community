/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CacheUpdateSession {
  private static final Logger LOG = Logger.getInstance("#" + CacheUpdateSession.class.getName());
  private final List<VirtualFile> myFilesToUpdate;
  private final int myJobsToDo;
  private final List<Pair<CacheUpdater, Collection<VirtualFile>>> myUpdatersWithFiles = new ArrayList<Pair<CacheUpdater, Collection<VirtualFile>>>();

  CacheUpdateSession(@NotNull Collection<CacheUpdater> updaters, @NotNull ProgressIndicator indicator) {
    List<CacheUpdater> processedUpdaters = new ArrayList<CacheUpdater>();

    LinkedHashSet<VirtualFile> set = ContainerUtil.newLinkedHashSet();
    try {
      int jobsCount = 0;
      for (CacheUpdater each : updaters) {
        indicator.checkCanceled();
        try {
          jobsCount += each.getNumberOfPendingUpdateJobs();
          List<VirtualFile> updaterFiles = Arrays.asList(each.queryNeededFiles(indicator));
          processedUpdaters.add(each);
          set.addAll(updaterFiles);
          myUpdatersWithFiles.add(Pair.create(each, (Collection<VirtualFile>)new THashSet<VirtualFile>(updaterFiles)));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
      myJobsToDo = jobsCount;
    }
    catch (ProcessCanceledException e) {
      for (CacheUpdater each : processedUpdaters) {
        each.canceled();
      }
      throw e;
    }
    myFilesToUpdate = ContainerUtil.newArrayList(set);
  }

  public int getNumberOfPendingUpdateJobs() {
    return myJobsToDo;
  }

  @NotNull
  public Collection<VirtualFile> getFilesToUpdate() {
    return myFilesToUpdate;
  }

  @Nullable
  private synchronized Pair<CacheUpdater, Collection<VirtualFile>> getPair(@NotNull final VirtualFile file) {
    return ContainerUtil.find(myUpdatersWithFiles, new Condition<Pair<CacheUpdater, Collection<VirtualFile>>>() {
      @Override
      public boolean value(Pair<CacheUpdater, Collection<VirtualFile>> cacheUpdaterCollectionPair) {
        return cacheUpdaterCollectionPair.second.contains(file);
      }
    });
  }

  public void processFile(@NotNull FileContent content) {
    VirtualFile file = content.getVirtualFile();
    boolean isValid = file.isValid() && !file.isDirectory();

    Throwable exception = null;
    while (true) {
      Pair<CacheUpdater, Collection<VirtualFile>> pair = getPair(file);
      if (pair == null) break;
      CacheUpdater eachUpdater = pair.getFirst();
      Collection<VirtualFile> eachFiles = pair.getSecond();
      try {
        if (isValid && exception == null) {
          eachUpdater.processFile(content);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        exception = e;
      }
      removeFile(file, eachUpdater, eachFiles);
    }
    if (exception instanceof RuntimeException) throw (RuntimeException)exception;
    if (exception instanceof Error) throw (Error)exception;
    if (exception != null) throw new RuntimeException(exception);
  }

  private synchronized void removeFile(@NotNull VirtualFile file, @NotNull CacheUpdater eachUpdater, @NotNull Collection<VirtualFile> eachFiles) {
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
    }
  }

  public void updatingDone() {
    for (Pair<CacheUpdater, Collection<VirtualFile>> eachPair : myUpdatersWithFiles) {
      try {
        CacheUpdater eachUpdater = eachPair.first;
        eachUpdater.updatingDone();
        if (!eachPair.second.isEmpty()) {
          LOG.error(CacheUpdater.class.getSimpleName() + " " + eachUpdater + " has not finished yet:\n" + new ArrayList<VirtualFile>(eachPair.second));
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
