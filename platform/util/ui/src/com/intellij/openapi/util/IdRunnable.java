// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.util.ui.update.ComparableObject;

public abstract class IdRunnable extends ComparableObject.Impl implements Runnable {

  public IdRunnable(Object object) {
    super(object);
  }

  public IdRunnable(Object[] objects) {
    super(objects);
  }
}
