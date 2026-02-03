// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PatchUtil {
  public static final int REGULAR_FILE_MODE = 100644;
  public static final int EXECUTABLE_FILE_MODE = 100755;
  @SuppressWarnings("unused") public static final int SYMBOLIC_LINK_MODE = 120000; //now we do not support such cases, but need to keep in mind
}
