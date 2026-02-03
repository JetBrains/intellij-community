// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.ChangeListener;
import java.util.EventListener;

public interface UserActivityProviderComponent extends EventListener {
  void addChangeListener(@NotNull ChangeListener changeListener);

  void removeChangeListener(@NotNull ChangeListener changeListener);
}
