// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.sun.jna.Library;

public interface NSTLibrary extends Library {
  void registerButtonText(String uid, String text, TBItemCallback action);
  void registerButtonImg(String uid, byte[] bytes, int bytesCount, TBItemCallback action);
}