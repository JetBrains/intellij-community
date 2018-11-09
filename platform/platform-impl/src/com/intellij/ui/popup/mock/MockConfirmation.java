// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup.mock;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class MockConfirmation extends ListPopupImpl {
  String myOnYesText;
  public MockConfirmation(ListPopupStep aStep, String onYesText) {
    super(aStep);
    myOnYesText = onYesText;
  }

  @Override
  public void showInCenterOf(@NotNull Component aContainer) {
    getStep().onChosen(myOnYesText, true);
  }

  @Override
  public void showUnderneathOf(@NotNull Component aComponent) {
    getStep().onChosen(myOnYesText, true);
  }
}
