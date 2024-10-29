// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public abstract class ChangeListTodosPanel extends TodoPanel {

  private final Alarm myAlarm;

  public ChangeListTodosPanel(@NotNull TodoView todoView,
                              @NotNull TodoPanelSettings settings,
                              @NotNull Content content) {
    super(todoView, settings, false, content);

    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListManagerListener(), this);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  }

  private void rebuildWithAlarm() {
    rebuildWithAlarm(myAlarm);
  }

  private void updateTabName() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      setDisplayName(getTabName(myProject));
    });
  }

  private final class MyChangeListManagerListener extends ChangeListAdapter {
    @Override
    public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
      rebuildWithAlarm();
      updateTabName();
    }

    @Override
    public void changeListRenamed(final ChangeList list, final String oldName) {
      updateTabName();
    }

    @Override
    public void changesMoved(final Collection<? extends Change> changes, final ChangeList fromList, final ChangeList toList) {
      rebuildWithAlarm();
    }

    @Override
    public void allChangeListsMappingsChanged() {
      updateTabName();
    }
  }

  @NotNull
  @NlsContexts.TabTitle
  static String getTabName(@NotNull Project project) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.areChangeListsEnabled()) {
      LocalChangeList list = changeListManager.getDefaultChangeList();
      String changelistName = list.getName().trim();
      String suffix = VcsBundle.message("todo.tab.title.changelist.suffix");
      return StringUtil.endsWithIgnoreCase(changelistName, suffix) ? changelistName
                                                                   : changelistName + " " + suffix;
    }
    else {
      return VcsBundle.message("todo.tab.title.all.changes");
    }
  }
}