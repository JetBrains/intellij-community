// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class PatchUtil {
  public final static int REGULAR_FILE_MODE = 100644;
  public final static int EXECUTABLE_FILE_MODE = 100755;
  @SuppressWarnings("unused")
  public final static int SYMBOLIC_LINK_MODE = 120000; //now we do not support such cases, but need to keep in mind
}
