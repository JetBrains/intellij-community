package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;

import java.util.List;

public interface ChangesBrowserExtender {
  void addToolbarActions(final DialogWrapper dialogWrapper);
  void addSelectedListChangeListener(SelectedListChangeListener listener);
  
  List<AbstractVcs> getAffectedVcses();
  List<Change> getCurrentIncludedChanges();
}
