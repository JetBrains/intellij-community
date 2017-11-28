// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.ui.popup.PopupStep;

public interface NextStepHandler {
  void handleNextStep(final PopupStep nextStep, Object parentValue);
}
