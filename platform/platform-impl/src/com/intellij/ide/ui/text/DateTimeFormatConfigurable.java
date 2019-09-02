// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.text;

import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.util.text.DateTimeFormatManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class DateTimeFormatConfigurable extends ConfigurableBase<DateTimeFormatConfigurableUi, DateTimeFormatManager> {
  protected DateTimeFormatConfigurable() {
    super("ide.date.format", "Date Formats", null);
  }

  @NotNull
  @Override
  protected DateTimeFormatManager getSettings() {
    return DateTimeFormatManager.getInstance();
  }

  @Override
  protected DateTimeFormatConfigurableUi createUi() {
    return new DateTimeFormatConfigurableUi(getSettings());
  }
}
