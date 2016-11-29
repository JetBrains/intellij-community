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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.components.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@State(
  name = "DslActivationStatus",
  storages = {
    @Storage(value = "dslActivation.xml", roamingType = RoamingType.DISABLED, deprecated = true),
    @Storage(value = "dslActivationStatus.xml", roamingType = RoamingType.DISABLED)
  }
)
public class DslActivationStatus implements PersistentStateComponent<DslActivationStatus.State> {
  enum Status {
    ACTIVE,
    MODIFIED,
    ERROR
  }

  public static class Entry implements Comparable<Entry> {
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry)o;

      if (url != null ? !url.equals(entry.url) : entry.url != null) return false;
      if (status != entry.status) return false;
      if (error != null ? !error.equals(entry.error) : entry.error != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = url != null ? url.hashCode() : 0;
      result = 31 * result + (status != null ? status.hashCode() : 0);
      result = 31 * result + (error != null ? error.hashCode() : 0);
      return result;
    }

    @Override
    public int compareTo(Entry o) {
      return equals(o) ? 0 : url.compareTo(o.url);
    }
  }

  public static class State {
    @AbstractCollection(surroundWithTag = false)
    public Collection<Entry> entries;

    public State(@NotNull Collection<Entry> entries) {
      this.entries = entries;
    }

    public State() {
      this(new SmartList<>());
    }
  }

  private final THashMap<VirtualFile, Entry> myStatus = new THashMap<>();

  @Nullable
  public Entry getGdslFileInfo(@NotNull VirtualFile file) {
    synchronized (myStatus) {
      return myStatus.get(file);
    }
  }

  @NotNull
  public Entry getGdslFileInfoOrCreate(@NotNull VirtualFile file) {
    Entry entry;
    synchronized (myStatus) {
      entry = myStatus.get(file);
      if (entry == null) {
        entry = new Entry(file.getUrl(), Status.ACTIVE, null);
        myStatus.put(file, entry);
      }
    }
    return entry;
  }

  @Nullable
  @Override
  public State getState() {
    synchronized (myStatus) {
      // remove default entries
      myStatus.retainEntries(new TObjectObjectProcedure<VirtualFile, Entry>() {
        @Override
        public boolean execute(VirtualFile file, Entry entry) {
          return !(entry.status == Status.ACTIVE && entry.error == null);
        }
      });

      if (myStatus.isEmpty()) {
        return new State(Collections.<Entry>emptyList());
      }

      Entry[] entries = myStatus.values().toArray(new Entry[myStatus.size()]);
      Arrays.sort(entries);
      return new State(Arrays.asList(entries));
    }
  }

  @Override
  public void loadState(State state) {
    synchronized (myStatus) {
      myStatus.clear();
      if (ContainerUtil.isEmpty(state.entries)) {
        return;
      }

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
