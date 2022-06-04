// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

/** @deprecated Please use {@link com.intellij.externalProcessAuthHelper.AuthenticationMode}*/
@Deprecated(forRemoval = true)
public enum GitAuthenticationMode {
  NONE, SILENT, FULL
}
