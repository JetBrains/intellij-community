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
package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
 * The latest state of the versioned item
 */
public class ItemLatestState {
  private final boolean myDefaultHead;
  /** version number */
  private final VcsRevisionNumber myNumber;
  /** true if the item still exists in the remote reposistory */
  private final boolean myItemExists;

  /**
   * A constructor
   *
   * @param number version number
   * @param itemExists true if the item still exists in the remote reposistory
   * @param defaultHead
   */

  public ItemLatestState(final VcsRevisionNumber number, final boolean itemExists, boolean defaultHead) {
    myNumber = number;
    myItemExists = itemExists;
    myDefaultHead = defaultHead;
  }

  /**
   * @return the latest version of the item
   */
  public VcsRevisionNumber getNumber() {
    return myNumber;
  }

  /**
   * @return true if the item exists in the remote reposistory
   */
  public boolean isItemExists() {
    return myItemExists;
  }

  public boolean isDefaultHead() {
    return myDefaultHead;
  }
}
