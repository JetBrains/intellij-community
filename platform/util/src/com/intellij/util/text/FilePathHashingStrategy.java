// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

public final class FilePathHashingStrategy {
  private FilePathHashingStrategy() { }

  public static @NotNull TObjectHashingStrategy<String> create() {
    return create(SystemInfo.isFileSystemCaseSensitive);
  }

  public static @NotNull TObjectHashingStrategy<CharSequence> createForCharSequence() {
    return createForCharSequence(SystemInfo.isFileSystemCaseSensitive);
  }

  public static @NotNull TObjectHashingStrategy<CharSequence> createForCharSequence(boolean caseSensitive) {
    return caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE;
  }

  public static @NotNull TObjectHashingStrategy<String> create(boolean caseSensitive) {
    return caseSensitive ? ContainerUtil.canonicalStrategy() : CaseInsensitiveStringHashingStrategy.INSTANCE;
  }
}
