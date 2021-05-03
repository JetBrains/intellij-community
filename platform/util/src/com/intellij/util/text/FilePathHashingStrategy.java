// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.SystemInfoRt;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class FilePathHashingStrategy {
  public static final TObjectHashingStrategy<CharSequence> INSTANCE = createForCharSequence(SystemInfoRt.isFileSystemCaseSensitive);

  private FilePathHashingStrategy() { }

  public static @NotNull TObjectHashingStrategy<String> create() {
    return create(SystemInfoRt.isFileSystemCaseSensitive);
  }

  public static @NotNull TObjectHashingStrategy<CharSequence> createForCharSequence(boolean caseSensitive) {
    return caseSensitive ? CharSequenceHashingStrategy.CASE_SENSITIVE : CharSequenceHashingStrategy.CASE_INSENSITIVE;
  }

  public static @NotNull TObjectHashingStrategy<String> create(boolean caseSensitive) {
    //noinspection unchecked
    return caseSensitive ? (TObjectHashingStrategy<String>)TObjectHashingStrategy.CANONICAL : CaseInsensitiveStringHashingStrategy.INSTANCE;
  }
}
