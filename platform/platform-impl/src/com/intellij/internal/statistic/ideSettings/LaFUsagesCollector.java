package com.intellij.internal.statistic.ideSettings;

import com.intellij.ide.ui.LafManager;
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

public class LaFUsagesCollector extends UsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages(@Nullable Project project) throws CollectUsagesException {
    UIManager.LookAndFeelInfo laf = LafManager.getInstance().getCurrentLookAndFeel();
    return laf != null ? Collections.singleton(new UsageDescriptor(SystemInfo.OS_NAME + " - " + laf.getName(), 1))
                       : Collections.<UsageDescriptor>emptySet();
  }

  @NotNull
  @Override
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create("Look and Feel");
  }
}
