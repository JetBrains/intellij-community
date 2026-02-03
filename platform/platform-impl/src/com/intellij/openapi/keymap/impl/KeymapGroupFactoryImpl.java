// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.NlsActions;

import javax.swing.*;

final class KeymapGroupFactoryImpl extends KeymapGroupFactory {
  @Override
  public KeymapGroup createGroup(@NlsActions.ActionText String name) {
    return new Group(name);
  }

  @Override
  public KeymapGroup createGroup(@NlsActions.ActionText String name, Icon icon) {
    return new Group(name, null, () -> icon);
  }
}
