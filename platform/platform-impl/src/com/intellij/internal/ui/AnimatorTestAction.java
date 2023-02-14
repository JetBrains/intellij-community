// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui;

import com.intellij.concurrency.JobScheduler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.AnimatedIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class AnimatorTestAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    ScheduledFuture<?> future = JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> SwingUtilities.invokeLater(() -> TimeoutUtil.sleep(30)), 0, 123, TimeUnit.MILLISECONDS);

    try {
      new DialogWrapper(e.getProject()) {
        {
          init();
        }

        @NotNull
        @Override
        protected JComponent createCenterPanel() {
          int cycles = 20;

          Icon passive = AllIcons.Process.Big.Step_passive;
          Icon[] icons1 = {
            AllIcons.Process.Big.Step_1,
            AllIcons.Process.Big.Step_2,
            AllIcons.Process.Big.Step_3,
            AllIcons.Process.Big.Step_4,
            AllIcons.Process.Big.Step_5,
            AllIcons.Process.Big.Step_6,
            AllIcons.Process.Big.Step_7,
            AllIcons.Process.Big.Step_8
          };
          List<Icon> iconsList2 = new ArrayList<>();
          for (int i = 0; i < cycles; i++) {
            Collections.addAll(iconsList2, icons1);
          }
          Icon[] icons2 = iconsList2.toArray(new Icon[0]);

          JPanel panel = new JPanel(new BorderLayout());
          AnimatedIcon animatedIcon1 = new AnimatedIcon("Casual", icons1, passive, 600);
          AnimatedIcon animatedIcon2 = new AnimatedIcon("Long", icons2, passive, 600 * cycles);
          animatedIcon1.resume();
          animatedIcon2.resume();
          panel.add(animatedIcon1, BorderLayout.WEST);
          panel.add(animatedIcon2, BorderLayout.EAST);
          return panel;
        }
      }.show();
    }
    finally {
      future.cancel(false);
    }
  }
}
