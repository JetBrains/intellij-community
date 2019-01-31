// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles interactive input requests from ssh, such as a passphrase request, an unknown server key confirmation, etc.
 */
public interface GitNativeSshAuthenticator {
  @Nullable
  String handleInput(@NotNull String description);
}
