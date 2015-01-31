/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.Page;
import com.intellij.util.io.PersistentEnumerator;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogHashMap;
import com.intellij.vcs.log.VcsLogProvider;
import com.intellij.vcs.log.impl.HashImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Supports the int <-> Hash persistent mapping.
 */
public class VcsLogHashMapImpl implements Disposable, VcsLogHashMap {

  public static final VcsLogHashMap EMPTY = new VcsLogHashMap() {
    @Override
    public int getCommitIndex(@NotNull Hash hash) {
      return 0;
    }

    @NotNull
    @Override
    public Hash getHash(int commitIndex) {
      return HashImpl.build("");
    }

    @Nullable
    @Override
    public Hash findHashByString(@NotNull String string) {
      return null;
    }
  };

  private static final File LOG_CACHE_APP_DIR = new File(new File(PathManager.getSystemPath(), "vcs-log"), "hashes");
  private static final Logger LOG = Logger.getInstance(VcsLogHashMap.class);
  private static final int VERSION = 1;

  private final PersistentEnumerator<Hash> myPersistentEnumerator;

  public VcsLogHashMapImpl(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> logProviders) throws IOException {
    cleanupOldNaming(project, logProviders);
    String logId = calcLogId(project, logProviders);
    final File mapFile = new File(LOG_CACHE_APP_DIR, logId + "." + VERSION);
    if (!mapFile.exists()) {
      IOUtil.deleteAllFilesStartingWith(new File(LOG_CACHE_APP_DIR, logId));
    }

    Disposer.register(project, this);
    myPersistentEnumerator = IOUtil.openCleanOrResetBroken(new ThrowableComputable<PersistentEnumerator<Hash>, IOException>() {
      @Override
      public PersistentEnumerator<Hash> compute() throws IOException {
        return new PersistentEnumerator<Hash>(mapFile, new MyHashKeyDescriptor(), Page.PAGE_SIZE);
      }
    }, mapFile);
  }

  @NotNull
  private static String calcLogId(@NotNull Project project, @NotNull final Map<VirtualFile, VcsLogProvider> logProviders) {
    int hashcode = calcLogProvidersHash(logProviders);
    return project.getLocationHash() + "." + Integer.toHexString(hashcode);
  }

  // TODO remove in IDEA 15
  private static void cleanupOldNaming(@NotNull Project project, @NotNull Map<VirtualFile, VcsLogProvider> providers) {
    int hashcode = calcLogProvidersHash(providers);
    String oldLogId = project.getName() + "." + hashcode;
    FileUtil.delete(new File(new File(PathManager.getSystemPath(), "vcs-log"), oldLogId));
  }

  private static int calcLogProvidersHash(@NotNull final Map<VirtualFile, VcsLogProvider> logProviders) {
    List<VirtualFile> sortedRoots = ContainerUtil.sorted(logProviders.keySet(), new Comparator<VirtualFile>() {
      @Override
      public int compare(@NotNull VirtualFile o1, @NotNull VirtualFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    return StringUtil.join(sortedRoots, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile root) {
        return root.getPath() + "." + logProviders.get(root).getSupportedVcs().getName();
      }
    }, ".").hashCode();
  }

  @Nullable
  private Hash doGetHash(int index) throws IOException {
    return myPersistentEnumerator.valueOf(index);
  }

  private int getOrPut(@NotNull Hash hash) throws IOException {
    return myPersistentEnumerator.enumerate(hash);
  }

  @Override
  public int getCommitIndex(@NotNull Hash hash) {
    try {
      return getOrPut(hash);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // TODO the map is corrupted => need to rebuild
    }
  }

  @Override
  @NotNull
  public Hash getHash(int commitIndex) {
    try {
      Hash hash = doGetHash(commitIndex);
      if (hash == null) {
        throw new RuntimeException("Unknown commit index: " + commitIndex); // TODO this shouldn't happen => need to recreate the map
      }
      return hash;
    }
    catch (IOException e) {
      throw new RuntimeException(e); // TODO map is corrupted => need to recreate it
    }
  }

  @Override
  @Nullable
  public Hash findHashByString(@NotNull String string) {
    final String pHash = string.toLowerCase();
    try {
      return findHash(new Condition<Hash>() {
        @Override
        public boolean value(@NotNull Hash hash) {
          return hash.toString().toLowerCase().startsWith(pHash);
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  public void flush() {
    myPersistentEnumerator.force();
  }

  @Override
  public void dispose() {
    try {
      myPersistentEnumerator.close();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Nullable
  Hash findHash(@NotNull final Condition<Hash> condition) throws IOException {
    final Ref<Hash> hashRef = Ref.create();
    myPersistentEnumerator.iterateData(new CommonProcessors.FindProcessor<Hash>() {
      @Override
      protected boolean accept(Hash hash) {
        boolean matches = condition.value(hash);
        if (matches) {
          hashRef.set(hash);
        }
        return matches;
      }
    });
    return hashRef.get();
  }

  private static class MyHashKeyDescriptor implements KeyDescriptor<Hash> {
    @Override
    public void save(@NotNull DataOutput out, Hash value) throws IOException {
      ((HashImpl)value).write(out);
    }

    @Override
    public Hash read(@NotNull DataInput in) throws IOException {
      return HashImpl.read(in);
    }

    @Override
    public int getHashCode(Hash value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(Hash val1, Hash val2) {
      return val1.equals(val2);
    }
  }
}
