// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

public interface VcsActivationListener extends EventListener {
  /**
   * @param activeVcses all active vcses. Can be empty.
   */
  void vcsesActivated(@NotNull List<AbstractVcs> activeVcses);
}