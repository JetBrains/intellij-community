// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;

/**
 * @author eldar
 */
public enum ProcessMachineType {
  UNKNOWN(0),
  I386(0x14c),
  AMD64(0x8664);

  private final short myPeCode;

  ProcessMachineType(int peCode) {
    myPeCode = (short)peCode;
  }

  @NotNull
  static ProcessMachineType forPeMachineTypeCode(short peCode) {
    for (ProcessMachineType value : values()) {
      if (value.myPeCode == peCode) return value;
    }
    return UNKNOWN;
  }
}
