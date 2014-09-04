/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class MultiSelectionListPopupStep<T> extends BaseListPopupStep<T> {
  private int[] myDefaultOptionIndices = ArrayUtil.EMPTY_INT_ARRAY;

  protected MultiSelectionListPopupStep(@Nullable String title, List<? extends T> values) {
    super(title, values);
  }

  public abstract PopupStep<?> onChosen(List<T> selectedValues, boolean finalChoice);

  public boolean hasSubstep(List<T> selectedValues) {
    return false;
  }

  @Override
  public final PopupStep onChosen(T selectedValue, boolean finalChoice) {
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
