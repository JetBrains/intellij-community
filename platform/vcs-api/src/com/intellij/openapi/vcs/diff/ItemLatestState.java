// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.diff;

import com.intellij.openapi.vcs.history.VcsRevisionNumber;

/**
 * The latest state of the versioned item.
 */
public class ItemLatestState {
  private final boolean myDefaultHead;
  private final VcsRevisionNumber myNumber;
  private final boolean myItemExists;

  /**
   * @param itemExists true if the item still exists in the remote repository
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

  public boolean isItemExists() {
    return myItemExists;
  }

  public boolean isDefaultHead() {
    return myDefaultHead;
  }
}
