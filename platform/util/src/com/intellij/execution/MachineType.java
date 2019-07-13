// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import org.jetbrains.annotations.NotNull;

/**
 * @author eldar
 */
public enum MachineType {
  UNKNOWN(0, 0),
  I386(0x14c, 0x03),
  AMD64(0x8664, 0x3e);

  private final short myPeCode;
  private final short myElfCode;

  MachineType(int peCode, int elfCode) {
    myPeCode = (short)peCode;
    myElfCode = (short)elfCode;
  }

  @NotNull
  static MachineType forPeMachineTypeCode(short peCode) {
    for (MachineType value : values()) {
      if (value.myPeCode == peCode) return value;
    }
    return UNKNOWN;
  }

  static MachineType forElfMachineTypeCode(short elfCode) {
    for (MachineType value : values()) {
      if (value.myElfCode == elfCode) return value;
    }
    return UNKNOWN;
  }
}
