package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.xdebugger.attach.XAttachDebugger;
import com.intellij.xdebugger.attach.XAttachRecentItemsMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DefaultAttachRecentItemsMatcher implements XAttachRecentItemsMatcher {
  @Override
  public @NotNull List<? extends AttachToProcessActionBase.AttachToProcessItem> getMatchingAttachItems(AttachToProcessActionBase.@NotNull RecentItem recentItem,
                                                                                                       @NotNull List<? extends AttachToProcessActionBase.AttachToProcessItem> allItems,
                                                                                                       boolean isFirstItem,
                                                                                                       @NotNull Project project,
                                                                                                       @NotNull UserDataHolder dataHolder) {

    ArrayList<AttachToProcessActionBase.AttachToProcessItem> result = new ArrayList<>();

    for (AttachToProcessActionBase.AttachToProcessItem currentItem : allItems) {
      boolean isSuitableItem = recentItem.getGroup().equals(currentItem.getGroup()) &&
                               recentItem.getProcessInfo().getCommandLine()
                                 .equals(currentItem.getProcessInfo().getCommandLine());

      if (!isSuitableItem) continue;

      List<XAttachDebugger> debuggers = currentItem.getDebuggers();
      int selectedDebugger = -1;
      for (int j = 0; j < debuggers.size(); j++) {
        XAttachDebugger debugger = debuggers.get(j);
        if (debugger.getDebuggerDisplayName().equals(recentItem.getDebuggerName())) {
          selectedDebugger = j;
          break;
        }
      }
      if (selectedDebugger == -1) continue;

      result.add(AttachToProcessActionBase.AttachToProcessItem.createRecentAttachItem(currentItem, isFirstItem && result.isEmpty(), debuggers, selectedDebugger,
                                                                                      project, dataHolder));
    }
    return result;
  }
}
