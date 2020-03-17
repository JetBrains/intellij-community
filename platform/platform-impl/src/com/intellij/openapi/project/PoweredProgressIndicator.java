// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

class PoweredProgressIndicator extends DelegatingProgressIndicator {
  private static final String DISABLED_VALUE = "-";
  private final double myPower;

  static ProgressIndicator apply(@NotNull ProgressIndicator indicator) {
    Double power = getPower();
    return power == null ? indicator : new PoweredProgressIndicator(indicator, power);
  }

  private PoweredProgressIndicator(@NotNull ProgressIndicator indicator, double power) {
    super(indicator);
    myPower = power;
  }

  @Override
  public void setFraction(double fraction) {
    double poweredFraction = Math.pow(fraction, myPower);
    super.setFraction(poweredFraction);
  }

  private static Double getPower() {
    String rawValue = Registry.stringValue("indexing.progress.indicator.power");
    if (DISABLED_VALUE.equals(rawValue)) return null;
    try {
      return Double.parseDouble(rawValue);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }
}
