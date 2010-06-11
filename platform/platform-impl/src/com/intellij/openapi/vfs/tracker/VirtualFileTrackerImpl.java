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
package com.intellij.openapi.vfs.tracker;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mike
 */
public class VirtualFileTrackerImpl implements VirtualFileTracker {
  private final Map<String, Set<VirtualFileListener>> myNonRefreshTrackers = new ConcurrentHashMap<String, Set<VirtualFileListener>>();
  private final Map<String, Set<VirtualFileListener>> myAllTrackers = new ConcurrentHashMap<String, Set<VirtualFileListener>>();

  public VirtualFileTrackerImpl(VirtualFileManager virtualFileManager) {
    virtualFileManager.addVirtualFileListener(new VirtualFileListener() {
      public void propertyChanged(final VirtualFilePropertyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.propertyChanged(event);
        }
      }

      public void contentsChanged(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.contentsChanged(event);
        }
      }

      public void fileCreated(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;
                                             
        for (VirtualFileListener listener : listeners) {
          listener.fileCreated(event);
        }
      }

      public void fileDeleted(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileDeleted(event);
        }
      }

      public void fileMoved(final VirtualFileMoveEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileMoved(event);
        }
      }

      public void fileCopied(final VirtualFileCopyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.fileCopied(event);
        }
      }

      public void beforePropertyChange(final VirtualFilePropertyEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforePropertyChange(event);
        }
      }

      public void beforeContentsChange(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeContentsChange(event);
        }
      }

      public void beforeFileDeletion(final VirtualFileEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeFileDeletion(event);
        }
      }

      public void beforeFileMovement(final VirtualFileMoveEvent event) {
        final Collection<VirtualFileListener> listeners = getListeners(event.getFile(), event.isFromRefresh());
        if (listeners == null) return;

        for (VirtualFileListener listener : listeners) {
          listener.beforeFileMovement(event);
        }
      }
    });
  }

  public void addTracker(
    @NotNull final String fileUrl,
    @NotNull final VirtualFileListener listener,
    final boolean fromRefreshOnly,
    @NotNull Disposable parentDisposable) {

    getSet(fileUrl, myAllTrackers).add(listener);

    if (!fromRefreshOnly) {
      getSet(fileUrl, myNonRefreshTrackers).add(listener);
    }

    Disposer.register(parentDisposable, new Disposable() {
      public void dispose() {
        removeListener(fileUrl, listener, myAllTrackers);

        if (!fromRefreshOnly) {
          removeListener(fileUrl, listener, myNonRefreshTrackers);
        }
      }
    });
  }

  private static void removeListener(String fileUrl, VirtualFileListener listener, Map<String, Set<VirtualFileListener>> map) {
    Set<VirtualFileListener> listeners = map.get(fileUrl);
    if (listeners == null) return;

    listeners.remove(listener);
    if (listeners.isEmpty()) {
      map.remove(fileUrl);
    }
  }

  private static Set<VirtualFileListener> getSet(final String fileUrl, final Map<String, Set<VirtualFileListener>> map) {
    Set<VirtualFileListener> listeners = map.get(fileUrl);

    if (listeners == null) {
      listeners = new ConcurrentHashSet<VirtualFileListener>();
      map.put(fileUrl, listeners);
    }
    return listeners;
  }

  @Nullable
  private Collection<VirtualFileListener> getListeners(VirtualFile virtualFile, boolean fromRefresh) {
    Set<VirtualFileListener> listeners = null;

    while (virtualFile != null) {
      final String url = virtualFile.getUrl();


      if (!fromRefresh) {
        listeners = addToSet(listeners, myNonRefreshTrackers.get(url));
      }
      else {
        listeners = addToSet(listeners, myAllTrackers.get(url));
      }

      virtualFile = virtualFile.getParent();
    }

    if (listeners == null || listeners.isEmpty()) return null;

    return listeners;
  }

  private static <T> Set<T> addToSet(Set<T> to, final Set<T> what) {
    if (what == null || what.size() == 0) return to;

    if (to == null) to = new HashSet<T>();
    to.addAll(what);
    return to;
  }
}
