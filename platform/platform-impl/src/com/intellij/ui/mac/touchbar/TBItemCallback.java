// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.sun.jna.Callback;

public abstract class TBItemCallback implements Callback {
  public abstract void callback();

  public static TBItemCallback createPrintTextCallback(String text) {
    return new TBItemCallback() {
      @Override
      public void callback() {
        System.out.println(text);
      }
    };
  }
}