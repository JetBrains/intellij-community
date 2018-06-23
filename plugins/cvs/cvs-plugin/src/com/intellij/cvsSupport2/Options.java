// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2;

import org.intellij.lang.annotations.MagicConstant;

public interface Options {
  int SHOW_DIALOG = 0;
  int PERFORM_ACTION_AUTOMATICALLY = 1;
  int DO_NOTHING = 2;

  @MagicConstant(valuesFromClass = Options.class)
  @interface Values {}
}