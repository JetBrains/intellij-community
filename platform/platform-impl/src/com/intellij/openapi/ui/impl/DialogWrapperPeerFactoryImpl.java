/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class DialogWrapperPeerFactoryImpl extends DialogWrapperPeerFactory {
  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, project, canBeParent);
  }

  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @Nullable Project project, boolean canBeParent, @NotNull DialogWrapper.IdeModalityType ideModalityType) {
    return new DialogWrapperPeerImpl(wrapper, project, canBeParent, ideModalityType);
  }

  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, canBeParent);
  }

  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, @NotNull Component parent, boolean canBeParent) {
    return new DialogWrapperPeerImpl(wrapper, parent, canBeParent);
  }

  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper, boolean canBeParent, DialogWrapper.IdeModalityType ideModalityType) {
    return new DialogWrapperPeerImpl(wrapper, (Window)null, canBeParent, ideModalityType);
  }

  @NotNull
  @Override
  public DialogWrapperPeer createPeer(@NotNull DialogWrapper wrapper,
                                      Window owner,
                                      boolean canBeParent,
                                      DialogWrapper.IdeModalityType ideModalityType) {
    return new DialogWrapperPeerImpl(wrapper, owner, canBeParent, ideModalityType);
  }
}