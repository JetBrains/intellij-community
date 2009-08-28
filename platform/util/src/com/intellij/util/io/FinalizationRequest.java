/*
 * @author max
 */
package com.intellij.util.io;

class FinalizationRequest {
  public final Page page;
  public final long finalizationId;

  public FinalizationRequest(final Page page, final long finalizationId) {
    this.page = page;
    this.finalizationId = finalizationId;
  }

  @Override
  public String toString() {
    return "FinalizationRequest[page = " + page + ", finalizationId = " + finalizationId + "]";
  }
}
