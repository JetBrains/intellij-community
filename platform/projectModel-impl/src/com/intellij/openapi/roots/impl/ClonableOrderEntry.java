/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
@ApiStatus.Internal
public interface ClonableOrderEntry {
  /**
   * Creates a detached copy of this order entry. The returned value may be passed to {@link ModifiableRootModel#addOrderEntry} method
   * only and cannot be used in any other way.
   */
  @NotNull
  OrderEntry cloneEntry(@NotNull ModifiableRootModel rootModel, @NotNull ProjectRootManagerImpl projectRootManager, @NotNull VirtualFilePointerManager filePointerManager);
}
