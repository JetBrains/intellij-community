package com.intellij.driver.model;

import java.io.Serial;
import java.io.Serializable;

public final class ProductVersion implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private final String productCode;
  private final boolean isSnapshot;
  private final int baselineVersion;
  private final String asString;

  public ProductVersion(String productCode, boolean isSnapshot, int baselineVersion, String asString) {
    this.productCode = productCode;
    this.isSnapshot = isSnapshot;
    this.baselineVersion = baselineVersion;
    this.asString = asString;
  }

  public boolean isSnapshot() {
    return isSnapshot;
  }

  public int getBaselineVersion() {
    return baselineVersion;
  }

  public String getAsString() {
    return asString;
  }

  public String getProductCode() {
    return productCode;
  }

  @Override
  public String toString() {
    return asString;
  }
}
