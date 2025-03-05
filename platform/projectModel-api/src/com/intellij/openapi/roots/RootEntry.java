// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

// todo ijpl-339 mark experimental
@ApiStatus.Internal
public record RootEntry(
  @NotNull VirtualFile root,
  @NotNull OrderEntry orderEntry
) {}
