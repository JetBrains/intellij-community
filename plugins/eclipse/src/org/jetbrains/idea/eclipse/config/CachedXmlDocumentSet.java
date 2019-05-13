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
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CachedXmlDocumentSet {
  private final Map<String, String> nameToDir = new THashMap<>();

  @Nullable
  public Element load(@NotNull String name, boolean refresh) throws IOException, JDOMException {
    assert nameToDir.containsKey(name) : name;

    VirtualFile file = getFile(name, refresh);
    if (file == null) {
      return null;
    }

    InputStream inputStream = file.getInputStream();
    try {
      return JDOMUtil.load(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  public String getParent(@NotNull String name) {
    return nameToDir.get(name);
  }

  public void register(@NotNull String name, @NotNull String path) {
    nameToDir.put(name, path);
  }

  public void unregister(@NotNull String name) {
    nameToDir.remove(name);
  }

  public boolean exists(@NotNull String name) {
    return getFile(name, false) != null;
  }

  @Nullable
  VirtualFile getFile(@NotNull String name, boolean refresh) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(new File(getParent(name), name));
    if (file != null && refresh) {
      file.refresh(false, true);
      if (!file.isValid()) {
        return null;
      }
    }
    return file;
  }

  @NotNull
  public List<String> getFilePaths() {
    List<String> list = new ArrayList<>(nameToDir.size());
    for (String name : nameToDir.keySet()) {
      list.add(getParent(name) + '/' + name);
    }
    return list;
  }
}
