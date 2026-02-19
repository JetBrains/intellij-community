// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import org.jetbrains.annotations.Nullable;

public interface NumericMnemonicItem {

  boolean digitMnemonicsEnabled();

  @Nullable Character getMnemonicChar();

}
