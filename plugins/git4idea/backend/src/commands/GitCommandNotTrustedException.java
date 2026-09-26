// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when the project is not trusted.
 * <p>
 * This reflects the trust state of the caller, not a property of the git executable. A cache
 * keyed by the executable, such as {@code git4idea.config.GitExecutableFileTester}, must not
 * store this failure: the same executable can succeed for a later, trusted caller.
 */
public final class GitCommandNotTrustedException extends IllegalStateException {
  public GitCommandNotTrustedException(@NotNull String message) {
    super(message);
  }
}
