// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import org.jetbrains.annotations.ApiStatus;

/** @deprecated Please use {@link com.intellij.externalProcessAuthHelper.AuthenticationMode}*/
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public enum GitAuthenticationMode {
  NONE, SILENT, FULL
}
