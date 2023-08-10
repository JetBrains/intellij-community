// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "DslActivationStatus", storages = @Storage(value = "dslActivationStatus.xml", roamingType = RoamingType.DISABLED))
public final class DslActivationStatus implements PersistentStateComponent<DslActivationStatus.State> {
  public enum Status {
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
    public @NlsSafe String error;

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
    @XCollection
    public Collection<Entry> entries;

    public State(@NotNull Collection<Entry> entries) {
      this.entries = entries;
    }

    public State() {
      this(new SmartList<>());
    }
  }

  private final Map<VirtualFile, Entry> myStatus = new HashMap<>();

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
      myStatus.entrySet().removeIf(entry -> entry.getValue().status == Status.ACTIVE && entry.getValue().error == null);
      if (myStatus.isEmpty()) {
        return new State(Collections.emptyList());
      }

      Entry[] entries = myStatus.values().toArray(new Entry[0]);
      Arrays.sort(entries);
      return new State(Arrays.asList(entries));
    }
  }

  @Override
  public void loadState(@NotNull State state) {
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
    return ApplicationManager.getApplication().getService(DslActivationStatus.class);
  }
}
