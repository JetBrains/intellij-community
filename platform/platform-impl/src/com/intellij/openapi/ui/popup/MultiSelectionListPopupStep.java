// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class MultiSelectionListPopupStep<T> extends BaseListPopupStep<T> {
  private int[] myDefaultOptionIndices = ArrayUtilRt.EMPTY_INT_ARRAY;

  protected MultiSelectionListPopupStep(@PopupTitle @Nullable String title, List<? extends T> values) {
    super(title, values);
  }

  public abstract PopupStep<?> onChosen(List<T> selectedValues, boolean finalChoice);

  public boolean hasSubstep(List<? extends T> selectedValues) {
    return false;
  }

  @Override
  public final PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
    return onChosen(Collections.singletonList(selectedValue), finalChoice);
  }

  @Override
  public final boolean hasSubstep(T selectedValue) {
    return hasSubstep(Collections.singletonList(selectedValue));
  }

  @Override
  public final int getDefaultOptionIndex() {
    return myDefaultOptionIndices.length > 0 ? myDefaultOptionIndices[0] : -1;
  }

  @Override
  public final void setDefaultOptionIndex(int defaultOptionIndex) {
    myDefaultOptionIndices = new int[]{defaultOptionIndex};
  }

  public int[] getDefaultOptionIndices() {
    return myDefaultOptionIndices;
  }

  public void setDefaultOptionIndices(int[] defaultOptionIndices) {
    myDefaultOptionIndices = defaultOptionIndices;
  }
}
