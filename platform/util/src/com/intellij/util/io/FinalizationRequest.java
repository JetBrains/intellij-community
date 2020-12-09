// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

final class FinalizationRequest {
  public final Page page;
  public final long finalizationId;

  FinalizationRequest(final Page page, final long finalizationId) {
    this.page = page;
    this.finalizationId = finalizationId;
  }

  @Override
  public String toString() {
    return "FinalizationRequest[page = " + page + ", finalizationId = " + finalizationId + "]";
  }
}
