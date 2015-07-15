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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@State(
  name = "DslActivationStatus",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivation.xml", roamingType = RoamingType.DISABLED, deprecated = true),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/dslActivationStatus.xml", roamingType = RoamingType.DISABLED)
  }
)
public class DslActivationStatus implements PersistentStateComponent<DslActivationStatus.State> {

  enum Status {
    ACTIVE,
    MODIFIED,
    ERROR
  }

  public static class Entry {
    @Attribute
    public String url;
    @Attribute
    public Status status;
    @Attribute
    public String error;

    public Entry() {
    }

    public Entry(String url, Status status, String error) {
      this.url = url;
      this.status = status;
      this.error = error;
    }
  }

  public static class State {
    @AbstractCollection(surroundWithTag = false)
    public Collection<Entry> entries = ContainerUtil.newArrayList();
  }

  private final Map<VirtualFile, Entry> myStatus = Collections.synchronizedMap(new FactoryMap<VirtualFile, Entry>() {
    @Nullable
    @Override
    protected DslActivationStatus.Entry create(VirtualFile key) {
      return new DslActivationStatus.Entry(key.getUrl(), Status.ACTIVE, null);
    }
  });

  public Entry getGdslFileInfo(@NotNull VirtualFile file) {
    return myStatus.get(file);
  }

  @Nullable
  @Override
  public State getState() {
    final State state = new State();
    state.entries = myStatus.values();
    return state;
  }

  @Override
  public void loadState(State state) {
    synchronized (myStatus) {
      myStatus.clear();
      if (state.entries == null) return;
      final VirtualFileManager fileManager = VirtualFileManager.getInstance();
      for (Entry entry : state.entries) {
        if (entry.url == null || entry.status == null) continue;
        final VirtualFile file = fileManager.findFileByUrl(entry.url);
        if (file != null) {
          myStatus.put(file, entry);
        }
      }
    }
  }

  public static DslActivationStatus getInstance() {
    return ServiceManager.getService(DslActivationStatus.class);
  }
}
