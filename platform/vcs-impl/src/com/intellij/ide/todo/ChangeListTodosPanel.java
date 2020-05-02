// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.todo;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;

import java.util.Collection;

public abstract class ChangeListTodosPanel extends TodoPanel{
  private final Alarm myAlarm;

  public ChangeListTodosPanel(Project project, TodoPanelSettings settings, Content content){
    super(project,settings,false,content);
    ChangeListManager.getInstance(project).addChangeListListener(new MyChangeListManagerListener(), this);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  }

  private final class MyChangeListManagerListener extends ChangeListAdapter {
    @Override
    public void defaultListChanged(final ChangeList oldDefaultList, final ChangeList newDefaultList) {
      rebuildWithAlarm(myAlarm);
      AppUIUtil.invokeOnEdt(() -> setDisplayName(TodoView.getTabNameForChangeList(newDefaultList.getName())));
    }

    @Override
    public void changeListRenamed(final ChangeList list, final String oldName) {
      AppUIUtil.invokeOnEdt(() -> setDisplayName(TodoView.getTabNameForChangeList(list.getName())));
    }

    @Override
    public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
      rebuildWithAlarm(myAlarm);
    }
  }
}