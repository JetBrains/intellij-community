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
import org.jetbrains.annotations.NotNull;

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
      updateTabName();
    }

    @Override
    public void changeListRenamed(final ChangeList list, final String oldName) {
      updateTabName();
    }

    @Override
    public void changesMoved(final Collection<Change> changes, final ChangeList fromList, final ChangeList toList) {
      rebuildWithAlarm(myAlarm);
    }

    @Override
    public void allChangeListsMappingsChanged() {
      updateTabName();
    }

    private void updateTabName() {
      AppUIUtil.invokeOnEdt(() -> {
        if (myProject != null) setDisplayName(getTabName(myProject));
      });
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