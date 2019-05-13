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
package com.intellij.openapi.vcs.impl;

public enum VcsInitObject {
  MAPPINGS(10, false),
  CHANGE_LIST_MANAGER(100, false),
  DIRTY_SCOPE_MANAGER(110, false),
  COMMITTED_CHANGES_CACHE(200, true),
  BRANCHES(250, true),
  REMOTE_REVISIONS_CACHE(300, true),
  AFTER_COMMON(400, true);

  private final int myOrder;
  // others do not depend on it
  private final boolean myCanBeLast;

  VcsInitObject(final int order, final boolean canBeLast) {
    myOrder = order;
    myCanBeLast = canBeLast;
  }

  public int getOrder() {
    return myOrder;
  }

  public boolean isCanBeLast() {
    return myCanBeLast;
  }
}
