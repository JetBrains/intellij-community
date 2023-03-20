// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;

@FunctionalInterface
public interface PageContentLoader {
  @NotNull ByteBuffer loadPageContent(final @NotNull PageImpl page) throws IOException;
}
