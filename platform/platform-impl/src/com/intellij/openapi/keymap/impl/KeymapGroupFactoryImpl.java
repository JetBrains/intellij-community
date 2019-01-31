// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.keymap.KeymapGroup;
import com.intellij.openapi.keymap.KeymapGroupFactory;
import com.intellij.openapi.keymap.impl.ui.Group;

import javax.swing.*;

/**
 * @author yole
 */
public class KeymapGroupFactoryImpl extends KeymapGroupFactory {
  @Override
  public KeymapGroup createGroup(final String name) {
    return new Group(name, null, null);
  }

  @Override
  public KeymapGroup createGroup(final String name, final Icon icon) {
    return new Group(name, icon);
  }
}
