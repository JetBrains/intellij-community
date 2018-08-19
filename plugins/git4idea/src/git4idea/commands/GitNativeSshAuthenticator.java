// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles "ask passphrase" requests from ssh
 */
public interface GitNativeSshAuthenticator {
  @Nullable
  String askPassphrase(@NotNull String description);
}
