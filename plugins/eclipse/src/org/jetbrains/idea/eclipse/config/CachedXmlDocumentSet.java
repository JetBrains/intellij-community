// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.config;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class CachedXmlDocumentSet {
  private final Map<String, String> nameToDir = new HashMap<>();

  @Nullable
  public Element load(@NotNull String name, boolean refresh) throws IOException, JDOMException {
    assert nameToDir.containsKey(name) : name;

    VirtualFile file = getFile(name, refresh);
    if (file == null) {
      return null;
    }

    try (InputStream inputStream = file.getInputStream()) {
      return JDOMUtil.load(inputStream);
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
}
