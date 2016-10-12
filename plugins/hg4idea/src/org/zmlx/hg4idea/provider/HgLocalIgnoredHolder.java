/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HgLocalIgnoredHolder implements Disposable {
  private static final Logger LOG = Logger.getInstance(HgLocalIgnoredHolder.class);
  @NotNull private final MergingUpdateQueue myUpdateQueue;
  @NotNull private final AtomicBoolean myInUpdateMode;
  @NotNull private final HgRepository myRepository;
  @NotNull private final Set<VirtualFile> myIgnoredSet;
  @NotNull private final ReentrantReadWriteLock SET_LOCK = new ReentrantReadWriteLock();


  public HgLocalIgnoredHolder(@NotNull HgRepository repository) {
    myRepository = repository;
    myIgnoredSet = ContainerUtil.newHashSet();
    myInUpdateMode = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue("HgIgnoreUpdate", 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  public void startRescan() {
    myUpdateQueue.queue(new Update("hgRescanIgnored") {
      @Override
      public boolean canEat(Update update) {
        return true;
      }

      @Override
      public void run() {
        if (myInUpdateMode.compareAndSet(false, true)) {
          rescanAllIgnored();
          myInUpdateMode.set(false);
        }
      }
    });
  }

  private void rescanAllIgnored() {
    Set<VirtualFile> ignored = ContainerUtil.newHashSet();
    try {
      ignored.addAll(new HgStatusCommand.Builder(false).ignored(true).build(myRepository.getProject())
                       .getFiles(myRepository.getRoot(), null));
    }
    catch (VcsException e) {
      LOG.error("Can't reload ignored files for: " + myRepository.getPresentableUrl(), e);
      return;
    }
    try {
      SET_LOCK.writeLock().lock();
      myIgnoredSet.clear();
      myIgnoredSet.addAll(ignored);
    }
    finally {
      SET_LOCK.writeLock().unlock();
    }
  }

  public boolean contains(@NotNull VirtualFile file) {
    try {
      SET_LOCK.readLock().lock();
      return myIgnoredSet.contains(file);
    }
    finally {
      SET_LOCK.readLock().unlock();
    }
  }

  public boolean isInUpdateMode() {
    return myInUpdateMode.get();
  }

  @NotNull
  public Set<VirtualFile> getIgnoredFiles() {
    try {
      SET_LOCK.readLock().lock();
      return myIgnoredSet;
    }
    finally {
      SET_LOCK.readLock().unlock();
    }
  }

  @Override
  public void dispose() {
    try {
      myUpdateQueue.cancelAllUpdates();
      SET_LOCK.writeLock().lock();
      myIgnoredSet.clear();
    }
    finally {
      SET_LOCK.writeLock().unlock();
    }
  }

  public int getSize() {
    return getIgnoredFiles().size();
  }
}
