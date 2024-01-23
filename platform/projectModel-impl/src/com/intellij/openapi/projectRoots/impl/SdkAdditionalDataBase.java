// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import org.jetbrains.annotations.NotNull;

public abstract class SdkAdditionalDataBase implements SdkAdditionalData {
  protected static final Logger LOG = Logger.getInstance(SdkAdditionalDataBase.class);

  private Throwable myCommitStackTrace = null;

  /**
   * This method is intended to commit encapsulated objects (mark them as read-only) if they require access control.
   *
   * @see SdkAdditionalData#markAsCommited()
   */
  protected abstract void markInternalsAsCommited(@NotNull Throwable commitStackTrace);

  @Override
  public final void markAsCommited() {
    if (myCommitStackTrace == null) {
      myCommitStackTrace = new Throwable("SdkAdditionalData commit trace");
      markInternalsAsCommited(myCommitStackTrace);
    }
  }

  /**
   * SdkAdditionalData is now a part of workspace model, so it can not be modified directly anymore. All setters in the additional data
   * should invoke this method before changing internal data.
   **/
  protected final void assertWritable() {
    if (!isWritable()) {
      LOG.error(new Throwable("Additional sdk data can't be directly modified, see javadoc for the assertion.", myCommitStackTrace));
    }
  }

  protected final boolean isWritable() {
    return myCommitStackTrace == null;
  }
}
