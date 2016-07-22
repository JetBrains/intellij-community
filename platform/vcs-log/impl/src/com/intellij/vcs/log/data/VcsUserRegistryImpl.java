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
package com.intellij.vcs.log.data;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.io.*;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.impl.VcsUserImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
public class VcsUserRegistryImpl implements Disposable, VcsUserRegistry {

  private static final File USER_CACHE_APP_DIR = new File(PathManager.getSystemPath(), "vcs-users");
  private static final Logger LOG = Logger.getInstance(VcsUserRegistryImpl.class);
  private static final int STORAGE_VERSION = 2;
  private static final PersistentEnumeratorBase.DataFilter ACCEPT_ALL_DATA_FILTER = id -> true;

  @Nullable private final PersistentEnumeratorBase<VcsUser> myPersistentEnumerator;
  @NotNull private final Interner<VcsUser> myInterner;

  VcsUserRegistryImpl(@NotNull Project project) {
    final File mapFile = new File(USER_CACHE_APP_DIR, project.getLocationHash() + "." + STORAGE_VERSION);
    myPersistentEnumerator = initEnumerator(mapFile);
    myInterner = new Interner<>();
  }

  @Nullable
  private PersistentEnumeratorBase<VcsUser> initEnumerator(@NotNull final File mapFile) {
    try {
      return IOUtil.openCleanOrResetBroken(() -> new PersistentBTreeEnumerator<>(mapFile, new MyDescriptor(), Page.PAGE_SIZE, null,
                                                                                 STORAGE_VERSION), mapFile);
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  @NotNull
  @Override
  public VcsUser createUser(@NotNull String name, @NotNull String email) {
    synchronized (myInterner) {
      return myInterner.intern(new VcsUserImpl(name, email));
    }
  }

  public void addUser(@NotNull VcsUser user) {
    try {
      if (myPersistentEnumerator != null) {
        myPersistentEnumerator.enumerate(user);
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public void addUsers(@NotNull Collection<VcsUser> users) {
    for (VcsUser user : users) {
      addUser(user);
    }
  }

  @Override
  @NotNull
  public Set<VcsUser> getUsers() {
    try {
      Collection<VcsUser> users = myPersistentEnumerator != null ?
                                  myPersistentEnumerator.getAllDataObjects(ACCEPT_ALL_DATA_FILTER) :
                                  Collections.<VcsUser>emptySet();
      return ContainerUtil.newHashSet(users);
    }
    catch (IOException e) {
      LOG.warn(e);
      return Collections.emptySet();
    }
  }

  public void flush() {
    if (myPersistentEnumerator != null) {
      myPersistentEnumerator.force();
    }
  }

  @Override
  public void dispose() {
    try {
      if (myPersistentEnumerator != null) {
        myPersistentEnumerator.close();
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  private class MyDescriptor implements KeyDescriptor<VcsUser> {
    @Override
    public void save(@NotNull DataOutput out, VcsUser value) throws IOException {
      IOUtil.writeUTF(out, value.getName());
      IOUtil.writeUTF(out, value.getEmail());
    }

    @Override
    public VcsUser read(@NotNull DataInput in) throws IOException {
      String name = IOUtil.readUTF(in);
      String email = IOUtil.readUTF(in);
      return createUser(name, email);
    }

    @Override
    public int getHashCode(VcsUser value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(VcsUser val1, VcsUser val2) {
      return val1.equals(val2);
    }
  }
}
