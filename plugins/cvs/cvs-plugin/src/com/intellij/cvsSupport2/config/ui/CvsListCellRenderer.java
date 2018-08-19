/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.config.ui;

import com.intellij.cvsSupport2.ui.AbstractListCellRenderer;
import com.intellij.icons.AllIcons;

import javax.swing.*;

/**
 * author: lesya
 */

public class CvsListCellRenderer extends AbstractListCellRenderer {

  @Override
  protected Icon getPresentableIcon(Object value) {
    if (value == null) return null;
    return AllIcons.Providers.Cvs;
  }

  @Override
  protected String getPresentableString(Object value) {
    if (value == null)
      return "";
    else
      return value.toString();
  }

}
