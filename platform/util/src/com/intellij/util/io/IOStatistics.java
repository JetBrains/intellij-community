// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;

final class IOStatistics {
  static final Logger LOG = Logger.getInstance(IOStatistics.class);

  static final boolean DEBUG = System.getProperty("io.access.debug") != null;

  /** Log time of storage flush operation, if it takes longer than ... */
  static final int MIN_IO_TIME_TO_REPORT_MS = 100;

  /**
   * Print enumerator statistics each time (valuesCount & KEYS_FACTOR_MASK) == 0
   *
   * @see PersistentBTreeEnumerator#enumerateImpl(Object, boolean, boolean)
   */
  static final int KEYS_FACTOR_MASK = 0xFFFF;

  static void dump(String msg) {
    LOG.info(msg);
  }

  private IOStatistics() { throw new AssertionError("Not for instantiation"); }
}
