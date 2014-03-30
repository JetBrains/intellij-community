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
package com.intellij.lang;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author peter
 */
public abstract class LanguagePerFileMappings<T> implements PersistentStateComponent<Element>, PerFileMappings<T> {

  private static final Logger LOG = Logger.getInstance("com.intellij.lang.LanguagePerFileMappings");

  private final Map<VirtualFile, T> myMappings = new HashMap<VirtualFile, T>();
  private final Project myProject;

  public LanguagePerFileMappings(final Project project) {
    myProject = project;
  }

  @Nullable
  protected FilePropertyPusher<T> getFilePropertyPusher() {
    return null;
  }

  @Override
  public Map<VirtualFile, T> getMappings() {
    synchronized (myMappings) {
      cleanup();
      return Collections.unmodifiableMap(myMappings);
    }
  }

  private void cleanup() {
    for (final VirtualFile file : new ArrayList<VirtualFile>(myMappings.keySet())) {
      if (file != null //PROJECT, top-level
          && !file.isValid()) {
        myMappings.remove(file);
      }
    }
  }

  @Override
  @Nullable
  public T getMapping(@Nullable VirtualFile file) {
    FilePropertyPusher<T> pusher = getFilePropertyPusher();
    T t = getMappingInner(file, myMappings, pusher == null? null : pusher.getFileDataKey());
    return t == null? getDefaultMapping(file) : t;
  }

  @Nullable
  protected static <T> T getMappingInner(@Nullable VirtualFile file, @Nullable Map<VirtualFile, T> mappings, @Nullable Key<T> pusherKey) {
    if (file instanceof VirtualFileWindow) {
      final VirtualFileWindow window = (VirtualFileWindow)file;
      file = window.getDelegate();
    }
    VirtualFile originalFile = file instanceof LightVirtualFile ? ((LightVirtualFile)file).getOriginalFile() : null;
    if (Comparing.equal(originalFile, file)) originalFile = null;

    if (file != null) {
      final T pushedValue = pusherKey == null? null : file.getUserData(pusherKey);
      if (pushedValue != null) return pushedValue;
    }
    if (originalFile != null) {
      final T pushedValue = pusherKey == null? null : originalFile.getUserData(pusherKey);
      if (pushedValue != null) return pushedValue;
    }
    if (mappings == null) return null;
    synchronized (mappings) {
      for (VirtualFile cur = file; ; cur = cur.getParent()) {
        T t = mappings.get(cur);
        if (t != null) return t;
        if (originalFile != null) {
          t = mappings.get(originalFile);
          if (t != null) return t;
          originalFile = originalFile.getParent();
        }
        if (cur == null) break;
      }
    }
    return null;
  }

  @Override
  public T chosenToStored(VirtualFile file, T value) {
    return value;
  }

  @Override
  public boolean isSelectable(T value) {
    return true;
  }

  @Override
  @Nullable
  public T getDefaultMapping(@Nullable final VirtualFile file) {
    return null;
  }

  @Nullable
  public T getImmediateMapping(@Nullable final VirtualFile file) {
    synchronized (myMappings) {
      return myMappings.get(file);
    }
  }

  @Override
  public void setMappings(final Map<VirtualFile, T> mappings) {
    final Collection<VirtualFile> oldFiles;
    synchronized (myMappings) {
      oldFiles = new ArrayList<VirtualFile>(myMappings.keySet());
      myMappings.clear();
      myMappings.putAll(mappings);
      cleanup();
    }
    handleMappingChange(mappings.keySet(), oldFiles, !getProject().isDefault());
  }

  public void setMapping(@Nullable final VirtualFile file, @Nullable T dialect) {
    synchronized (myMappings) {
      if (dialect == null) {
        myMappings.remove(file);
      }
      else {
        myMappings.put(file, dialect);
      }
    }
    final List<VirtualFile> files = ContainerUtil.createMaybeSingletonList(file);
    handleMappingChange(files, files, false);
  }

  private void handleMappingChange(final Collection<VirtualFile> files, Collection<VirtualFile> oldFiles, final boolean includeOpenFiles) {
    final FilePropertyPusher<T> pusher = getFilePropertyPusher();
    if (pusher != null) {
      for (VirtualFile oldFile : oldFiles) {
        if (oldFile == null) continue; // project
        oldFile.putUserData(pusher.getFileDataKey(), null);
      }
      PushedFilePropertiesUpdater updater = PushedFilePropertiesUpdater.getInstance(myProject);
      if (updater == null) {
        if (!myProject.isDefault()) {
          LOG.error("updater = null. project=" + myProject.getName()+", this="+getClass().getSimpleName());
        }
      }
      else {
        updater.pushAll(pusher);
      }
    }
    if (shouldReparseFiles()) {
      PsiDocumentManager.getInstance(myProject).reparseFiles(files, includeOpenFiles);
    }
  }

  @Override
  public Collection<T> getAvailableValues(VirtualFile file) {
    return getAvailableValues();
  }

  protected abstract List<T> getAvailableValues();

  @Nullable
  protected abstract String serialize(T t);

  @Override
  public Element getState() {
    synchronized (myMappings) {
      cleanup();
      final Element element = new Element("x");
      final List<VirtualFile> files = new ArrayList<VirtualFile>(myMappings.keySet());
      Collections.sort(files, new Comparator<VirtualFile>() {
        @Override
        public int compare(final VirtualFile o1, final VirtualFile o2) {
          if (o1 == null || o2 == null) return o1 == null ? o2 == null ? 0 : 1 : -1;
          return o1.getPath().compareTo(o2.getPath());
        }
      });
      for (VirtualFile file : files) {
        final T dialect = myMappings.get(file);
        String value = serialize(dialect);
        if (value != null) {
          final Element child = new Element("file");
          element.addContent(child);
          child.setAttribute("url", file == null ? "PROJECT" : file.getUrl());
          child.setAttribute(getValueAttribute(), value);
        }
      }
      return element;
    }
  }

  @Nullable
  protected T handleUnknownMapping(VirtualFile file, String value) {
    return null;
  }

  @NotNull
  protected String getValueAttribute() {
    return "dialect";
  }

  @Override
  public void loadState(final Element state) {
    synchronized (myMappings) {
      final THashMap<String, T> dialectMap = new THashMap<String, T>();
      for (T dialect : getAvailableValues()) {
        String key = serialize(dialect);
        if (key != null) {
          dialectMap.put(key, dialect);
        }
      }
      final List<Element> files = state.getChildren("file");
      for (Element fileElement : files) {
        final String url = fileElement.getAttributeValue("url");
        final String dialectID = fileElement.getAttributeValue(getValueAttribute());
        final VirtualFile file = url.equals("PROJECT") ? null : VirtualFileManager.getInstance().findFileByUrl(url);
        T dialect = dialectMap.get(dialectID);
        if (dialect == null) {
          dialect = handleUnknownMapping(file, dialectID);
          if (dialect == null) continue;
        }
        if (file != null || url.equals("PROJECT")) {
          myMappings.put(file, dialect);
        }
      }
    }
  }

  @TestOnly
  public void cleanupForNextTest() {
    synchronized (myMappings) {
      myMappings.clear();
    }
  }

  protected Project getProject() {
    return myProject;
  }

  protected boolean shouldReparseFiles() {
    return true;
  }

}
