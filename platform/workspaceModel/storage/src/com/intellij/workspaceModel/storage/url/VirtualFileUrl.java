// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

public interface VirtualFileUrl {
  String getUrl();

  @ApiStatus.Internal
  List<String> getUrlSegments();

  default String getUrlWithoutTrailingSlash() {
    String url = getUrl();
    if (!url.isEmpty() && url.charAt(url.length() -1) == '/') {
      return url.substring(0, url.length() - 1);
    }
    return url;
  }

  String getFileName();

  /**
   * @return representation of the url without protocol
   */
  String getPresentableUrl();

  /**
   * @return the list of descendants for the current node
   */
  List<VirtualFileUrl> getSubTreeFileUrls();

  /**
   * Resolve the given path against virtual url
   * @param relativePath for resolve
   * @return instance representing the resolved path
   */
  VirtualFileUrl append(String relativePath);
}
