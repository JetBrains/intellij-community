// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.registry.Registry;

/**
 * Provides information about Vcs Log index features that are required for the plugin.
 */
public interface VcsLogIndexService {
  ExtensionPointName<VcsLogIndexService> VCS_LOG_INDEX_SERVICE_EP = ExtensionPointName.create("com.intellij.vcsLogIndexService");

  /**
   * Whether plugin needs forward index for paths.
   *
   * @return true if forward paths index is required.
   */
  boolean requiresPathsForwardIndex();

  static boolean isPathsForwardIndexRequired() {
    if (Registry.is("vcs.log.index.paths.forward.index.on")) return true;

    VcsLogIndexService[] extensions = Extensions.getExtensions(VCS_LOG_INDEX_SERVICE_EP);
    for (VcsLogIndexService indexService : extensions) {
      if (indexService.requiresPathsForwardIndex()) return true;
    }
    return false;
  }
}
