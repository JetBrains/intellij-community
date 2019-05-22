// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;

/**
 * @author eldar
 */
public enum ProcessMachineType {
  UNKNOWN(0, 0),
  I386(0x14c, 0x03),
  AMD64(0x8664, 0x3e);

  private final short myPeCode;
  private final short myElfCode;

  ProcessMachineType(int peCode, int elfCode) {
    myPeCode = (short)peCode;
    myElfCode = (short)elfCode;
  }

  @NotNull
  static ProcessMachineType forPeMachineTypeCode(short peCode) {
    for (ProcessMachineType value : values()) {
      if (value.myPeCode == peCode) return value;
    }
    return UNKNOWN;
  }

  static ProcessMachineType forElfMachineTypeCode(short elfCode) {
    for (ProcessMachineType value : values()) {
      if (value.myElfCode == elfCode) return value;
    }
    return UNKNOWN;
  }
}
