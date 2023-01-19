/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface LibraryOrSdkOrderEntry extends OrderEntry {
  /**
   * Returns files configured as roots in the corresponding library or SDK. Note that only existing files are returned; if you need to get
   * paths to non-existing files as well, use {@link #getRootUrls(OrderRootType)} instead.
   */
  VirtualFile @NotNull [] getRootFiles(@NotNull OrderRootType type);

  /**
   * Returns URLs of roots configured in the corresponding library or SDK.
   */
  String @NotNull [] getRootUrls(@NotNull OrderRootType type);

  /**
   * @deprecated use {@link #getRootFiles(OrderRootType)} instead; meaning of this method coming from the base {@link OrderEntry} interface 
   * is unclear.
   */
  @Deprecated
  @Override
  default VirtualFile @NotNull [] getFiles(@NotNull OrderRootType type) {
    return getRootFiles(type);
  }

  /**
   * @deprecated use {@link #getRootUrls(OrderRootType)} instead; meaning of this method coming from the base {@link OrderEntry} interface
   * is unclear.
   */
  @Deprecated
  @Override
  default String @NotNull [] getUrls(@NotNull OrderRootType rootType) {
    return getRootUrls(rootType);
  }
}
