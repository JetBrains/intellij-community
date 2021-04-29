// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.vcs.AbstractVcs;

import java.util.Collection;


interface ModuleVcsListener {
  void activeVcsSetChanged(Collection<? extends AbstractVcs> activeVcses);
}