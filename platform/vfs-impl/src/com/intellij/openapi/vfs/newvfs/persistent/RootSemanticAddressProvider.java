// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An extension intended to make vfs roots urls universal and shareable between different computers.
 * For every url we may have certain number of logical addresses from different providers.
 * On the other hand every logical address should be mapped to url in a unique way.
 *
 * For example: for jars inside .m2 we'd like to remove user-s home part of path.
 */
public interface RootSemanticAddressProvider {
  default List<Pair<String, SemanticLabel>> getPredefinedSemanticRoots() {
    return List.of();
  }
}
