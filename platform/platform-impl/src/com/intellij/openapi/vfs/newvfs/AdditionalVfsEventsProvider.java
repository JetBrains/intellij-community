// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Make additional events if some file system mirrors or extends another one.
 *
 * @see VfsImplUtil#getJarInvalidationEvents(VFileEvent, List)
 */
@ApiStatus.Experimental
public interface AdditionalVfsEventsProvider {
  ExtensionPointName<AdditionalVfsEventsProvider> EP_NAME = ExtensionPointName.create("com.intellij.additionalVfsEventsProvider");

  @NotNull List<@NotNull VFileEvent> getAdditionalEvents(@NotNull VFileEvent event);

  @NotNull static List<@NotNull VFileEvent> getAllAdditionalEvents(@NotNull VFileEvent event) {
    var result = new ArrayList<VFileEvent>();
    EP_NAME.getExtensionList().forEach(p -> result.addAll(p.getAdditionalEvents(event)));
    return result;
  }
}
