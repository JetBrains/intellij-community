// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;

@ApiStatus.Experimental
public class IdeaCancellationManager extends CancellationManager.TimeoutCancellationManager {

  private final int maxClassLength;

  public IdeaCancellationManager(int maxMethodTimeoutSec, int maxClassLength) {
    super(maxMethodTimeoutSec);
    this.maxClassLength = maxClassLength;
  }

  @Override
  public void checkCanceled(@Nullable StructClass classStruct) throws CanceledException {
    if (classStruct != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      int methodsLength = 0;
      for (StructMethod method : classStruct.getMethods()) {
        methodsLength += method.getCodeLength();
      }
      if (maxClassLength > 0 && methodsLength > maxClassLength) {
        throw new TimeExceedException();
      }
    }
    try {
      ProgressManager.checkCanceled();
    }
    catch (ProcessCanceledException e) {
      throw new CanceledException(e);
    }
    super.checkCanceled(null);
  }
}
