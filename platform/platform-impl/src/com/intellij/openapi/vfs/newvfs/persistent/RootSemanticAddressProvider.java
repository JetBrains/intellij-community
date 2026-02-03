// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An extension intended to make vfs roots urls universal and shareable between different computers.
 * For every url we may have certain number of logical addresses from different providers.
 * On the other hand every logical address should be mapped to url in a unique way.
 * <p>
 * For example: for jars inside .m2 we'd like to remove user-s home part of path.
 * <p>
 * Note: paths in urls should be normalized.
 *
 * @see com.intellij.openapi.util.io.FileUtil#normalize(String)
 */
public interface RootSemanticAddressProvider {
  @NotNull List<Pair<String, SemanticLabel>> getPredefinedSemanticRoots();
}
