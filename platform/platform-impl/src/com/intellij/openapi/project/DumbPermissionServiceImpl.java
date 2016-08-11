/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DumbPermissionServiceImpl implements DumbPermissionService {
  private final ThreadLocal<DumbModePermission> myPermission = new ThreadLocal<>();

  @Override
  public void allowStartingDumbModeInside(@NotNull DumbModePermission permission, @NotNull Runnable runnable) {
    DumbModePermission prev = myPermission.get();
    if (prev == DumbModePermission.MAY_START_MODAL && permission == DumbModePermission.MAY_START_BACKGROUND) {
      runnable.run();
      return;
    }

    myPermission.set(permission);
    try {
      runnable.run();
    }
    finally {
      if (prev == null) {
        myPermission.remove();
      } else {
        myPermission.set(prev);
      }
    }
  }

  @Nullable
  public DumbModePermission getPermission() {
    return myPermission.get();
  }
}
