// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;

final class JavaThreadingAnnotationChecks {
  @RequiresEdt
  static boolean edt() {
    return true;
  }

  @RequiresBackgroundThread
  static boolean backgroundThread() {
    return true;
  }

  @RequiresReadLock
  static boolean readLock() {
    return true;
  }

  @RequiresWriteLock
  static boolean writeLock() {
    return true;
  }

  @RequiresReadLockAbsence
  static boolean readLockAbsence() {
    return true;
  }
}
