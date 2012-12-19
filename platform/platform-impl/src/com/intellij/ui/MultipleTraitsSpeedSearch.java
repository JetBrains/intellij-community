/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.PairConvertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/13/12
 * Time: 8:02 PM
 */
public abstract class MultipleTraitsSpeedSearch<Comp extends JComponent> extends SpeedSearchBase<Comp> {
  protected final List<PairConvertor<Object, String, Boolean>> myOrderedConvertors;

  public MultipleTraitsSpeedSearch(Comp component, @NotNull List<PairConvertor<Object, String, Boolean>> convertors) {
    super(component);
    myOrderedConvertors = convertors;
  }

  @Override
  protected boolean isMatchingElement(Object element, String pattern) {
    for (PairConvertor<Object, String, Boolean> convertor : myOrderedConvertors) {
      final Boolean matched = convertor.convert(element, pattern);
      if (Boolean.TRUE.equals(matched)) return true;
    }
    return false;
  }

  @Nullable
  @Override
  protected final String getElementText(Object element) {
    throw new IllegalStateException();
  }
}
