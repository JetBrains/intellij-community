package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface CommittedChangeListDecorator {
  @Nullable
  Icon decorate(final CommittedChangeList list);
}
