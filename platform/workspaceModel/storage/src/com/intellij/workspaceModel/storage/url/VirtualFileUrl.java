// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.url;

import java.util.List;

/**
 * Represents a reference to a file or directory. This interface must be used instead of {@link String} for properties of workspace model
 * entities. It stores a URL of the file inside in a compact form.
 * <br>
 * Use {@link VirtualFileUrlManager#fromUrl} or {@link com.intellij.workspaceModel.ide.VirtualFileUrls#toVirtualFileUrl toVirtualFileUrl}
 * extension function to get an instance of this interface. 
 * Use {@link com.intellij.workspaceModel.ide.VirtualFileUrls#getVirtualFile virtualFile} extension property to locate a 
 * {@link com.intellij.openapi.vfs.VirtualFile VirtualFile} instance by an instance of this interface.
 */
public interface VirtualFileUrl {
  /**
   * Returns URL in the Virtual File System format.
   */
  String getUrl();

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
