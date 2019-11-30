// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.openapi.wm.impl.StripeButton;
import com.intellij.openapi.wm.impl.WindowInfoImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Apply {@code info} to the corresponded tool button and decorator.
 * Command uses freezed copy of passed {@code info} object.
 */
public final class ApplyWindowInfoCmd extends FinalizableCommand {
  private final WindowInfoImpl myInfo;
  private final StripeButton myButton;
  private final InternalDecorator myDecorator;

  public ApplyWindowInfoCmd(@NotNull WindowInfoImpl info,
                            @NotNull StripeButton button,
                            @NotNull InternalDecorator decorator,
                            @NotNull Runnable finishCallBack) {
    super(finishCallBack);
    myInfo = info.copy();
    myButton = button;
    myDecorator = decorator;
  }

  @Override
  public final void run() {
    try {
      myButton.apply(myInfo);
      myDecorator.apply(myInfo);
    }
    finally {
      finish();
    }
  }
}
