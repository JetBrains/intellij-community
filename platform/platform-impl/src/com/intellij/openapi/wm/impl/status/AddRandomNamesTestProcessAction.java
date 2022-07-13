// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class AddRandomNamesTestProcessAction extends AddTestProcessAction implements DumbAware {
  private final Random myRandom = new Random();

  public AddRandomNamesTestProcessAction() {
    super("Add Random Names Test Process");
  }

  @Override
  protected @Nullable String getProcessName(int num) {
    int length = myRandom.nextInt(8);
    return String.format("%s%d", "Test ".repeat(length), num);
  }
}
