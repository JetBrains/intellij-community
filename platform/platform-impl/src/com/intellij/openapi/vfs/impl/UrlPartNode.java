// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** Node which stores explicit 'name' instead of nameId.
 * The latter can be unavailable (e.g. when creating the pointer from the url of non-yet-existing file)
 * or incorrect (e.g. when creating the pointer from the url "/x/y/Z.TXT" for the file "z.txt" on case-insensitive file system)
 * As soon as the corresponding file got created, this UrlPartNode is replaced with FilePointerPartNode, which contains nameId and is faster and more succinct
 */
class UrlPartNode extends FilePointerPartNode {
  @NotNull
  private final String name;

  UrlPartNode(@NotNull String name, @NotNull FilePointerPartNode parent) {
    super(parent);
    this.name = name;
    if (SystemInfo.isUnix) {
      if (name.isEmpty()) {
        throw new IllegalArgumentException('\'' + name + '\'');
      }
    }
    else {
      if (StringUtil.isEmptyOrSpaces(name)) {
        throw new IllegalArgumentException('\'' + name + '\'');
      }
    }
  }

  @Override
  boolean urlEndsWithName(@NotNull String urlAfter, VirtualFile fileAfter) {
    return StringUtil.endsWith(urlAfter, getName());
  }

  @NotNull
  @Override
  CharSequence getName() {
    return name;
  }

  @Override
  boolean nameEqualTo(int nameId) {
    return FileUtil.PATH_CHAR_SEQUENCE_HASHING_STRATEGY.equals(getName(), fromNameId(nameId));
  }

  @Override
  public String toString() {
    return "UrlPartNode: '"+getName() + "' -> "+children.length;
  }
}
