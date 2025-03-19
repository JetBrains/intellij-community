// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Info {
  public String text;

  public @Nullable List<TextRange> ranges = new ArrayList<>();

  public final List<FoldingInfo> foldings = new ArrayList<>();
}
