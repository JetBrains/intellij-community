// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.url;

import com.intellij.platform.workspace.storage.EntityStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a reference to a file or directory. 
 * Workspace model entities must use properties of this type to store references to files instead of storing their paths or URLs as 
 * {@link String} properties, because it consumes less memory and provide efficient way to locate a {@link com.intellij.openapi.vfs.VirtualFile VirtualFile}.
 * <p>
 * Use {@link VirtualFileUrlManager#fromUrl} or {@link com.intellij.platform.backend.workspace.VirtualFileUrls#toVirtualFileUrl toVirtualFileUrl}
 * extension function to get an instance of this interface. 
 * Use {@link com.intellij.platform.backend.workspace.VirtualFileUrls#getVirtualFile virtualFile} extension property to locate a 
 * {@link com.intellij.openapi.vfs.VirtualFile VirtualFile} instance by an instance of this interface.
 * <p>
 * {@link EntityStorage#getVirtualFileUrlIndex()} provides a way to quickly find entities referring to a particular {@link VirtualFileUrl}.
 * Also, it's possible to automatically update references in entities when corresponding files are moved or renamed. Currently, it's 
 * implemented to specific types of entities only in {@link com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher VirtualFileUrlWatcher}.
 */
public interface VirtualFileUrl {
  /**
   * Returns URL in the Virtual File System format.
   */
  String getUrl();

  String getFileName();

  /**
   * @return the parent of the VirtualFileUrl, or null if there are no parent.
   */
  @Nullable VirtualFileUrl getParent();

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
