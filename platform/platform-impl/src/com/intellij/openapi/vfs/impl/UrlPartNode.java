// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Node which stores explicit 'name' instead of nameId.
 * The latter can be unavailable (e.g. when creating the pointer from the url of non-yet-existing file)
 * or incorrect (e.g. when creating the pointer from the url "/x/y/Z.TXT" for the file "z.txt" on case-insensitive file system)
 * As soon as the corresponding file got created, this UrlPartNode is replaced with FilePartNode, which contains nameId and is faster and more succinct
 */
final class UrlPartNode extends FilePartNode {
  private final @NotNull String name;

  UrlPartNode(@NotNull String name, @NotNull String parentUrl, @NotNull NewVirtualFileSystem fs) {
    super(fs);
    this.name = name;
    myFileOrUrl = childUrl(parentUrl, name, fs);
    if (name.isEmpty()) {
      throw new IllegalArgumentException('\'' + name + '\'');
    }
  }

  @NotNull
  @Override
  CharSequence getName() {
    return name;
  }

  @Override
  boolean nameEqualTo(int nameId) {
    return StringUtilRt.equal(getName(), fromNameId(nameId), SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Override
  public @NonNls String toString() {
    return "UrlPartNode: '"+getName() + "'; children:"+children.length;
  }
}
