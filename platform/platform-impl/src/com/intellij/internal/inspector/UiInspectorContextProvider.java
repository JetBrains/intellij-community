// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import java.util.List;

/**
 * Allows to pass additional information about component to the UI Inspector.
 * <p>
 * Can be registered on a {@link javax.swing.JComponent} using {@link UiInspectorUtil#registerProvider}
 * or implemented by {@link java.awt.Component}/{@link javax.accessibility.AccessibleContext} directly.
 */
public interface UiInspectorContextProvider {
  List<PropertyBean> getUiInspectorContext();
}
