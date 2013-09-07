/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AccessToNonThreadSafeStaticFieldFromInstanceInspection extends AccessToNonThreadSafeStaticFieldFromInstanceInspectionBase {

  public AccessToNonThreadSafeStaticFieldFromInstanceInspection() {}

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return UiUtils.createTreeClassChooserList(
      nonThreadSafeClasses, InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.option.title"),
      InspectionGadgetsBundle.message("access.to.non.thread.safe.static.field.from.instance.class.chooser.title"));
  }
}