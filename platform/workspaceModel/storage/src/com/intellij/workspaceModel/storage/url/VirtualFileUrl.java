// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url;

import java.util.List;

public interface VirtualFileUrl {
  String getUrl();
  String getFileName();
  String getPresentableUrl();
  List<VirtualFileUrl> getSubTreeFileUrls();
  VirtualFileUrl append(String relativePath);
}
