// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DumpInvalidTipsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(DumpInvalidTipsAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<TipAndTrickBean> allTips = TipAndTrickBean.EP_NAME.getExtensionList();
    List<TipAndTrickBean> invalidTips =
      ContainerUtil.filter(allTips, bean -> TipUIUtil.getTipText(bean, null).contains("No shortcut is assigned for the action"));

    if (invalidTips.isEmpty()) {
      LOG.warn("There is no invalid tips among " + allTips.size() + " listed");
    } else {
      LOG.warn("Tips that promote actions with no shortcut:");
      for (TipAndTrickBean tip : invalidTips) {
        LOG.warn(tip.toString());
      }
    }
  }
}
