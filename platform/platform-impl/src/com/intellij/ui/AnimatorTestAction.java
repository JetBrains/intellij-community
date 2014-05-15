package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AnimatedIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AnimatorTestAction extends AnAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    ScheduledThreadPoolExecutor worker = ConcurrencyUtil.newSingleScheduledThreadExecutor("DumbWorker");
    worker.scheduleWithFixedDelay(new Runnable() {
      @Override
      public void run() {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            try {
              Thread.sleep(30);
            }
            catch (InterruptedException ignore) {
            }
          }
        });
      }
    }, 0, 123, TimeUnit.MILLISECONDS);

    new DialogWrapper(e.getProject()) {
      {
        init();
      }

      @Nullable
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
          AllIcons.Process.Big.Step_8,
          AllIcons.Process.Big.Step_9,
          AllIcons.Process.Big.Step_10,
          AllIcons.Process.Big.Step_11,
          AllIcons.Process.Big.Step_12
        };
        List<Icon> iconsList2 = new ArrayList<Icon>();
        for (int i = 0; i < cycles; i++) {
          Collections.addAll(iconsList2, icons1);
        }
        Icon[] icons2 = ContainerUtil.toArray(iconsList2, new Icon[iconsList2.size()]);

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

    worker.shutdown();
  }
}
