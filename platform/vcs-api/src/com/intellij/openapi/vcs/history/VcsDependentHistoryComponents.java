/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.history;

import com.intellij.ui.EditorNotificationPanel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class VcsDependentHistoryComponents {
  private final ColumnInfo<?, ?>[] myColumns;
  private final @Nullable Consumer<VcsFileRevision> myRevisionListener;
  private final @Nullable JComponent myDetailsComponent;

  private final @Nullable EditorNotificationPanel myNotificationPanel;

  public VcsDependentHistoryComponents(ColumnInfo<?, ?>[] columns, @Nullable Consumer<VcsFileRevision> revisionListener, @Nullable JComponent detailsComponent,
                                       @Nullable EditorNotificationPanel notificationPanel) {
    myColumns = columns;
    myRevisionListener = revisionListener;
    myDetailsComponent = detailsComponent;
    myNotificationPanel = notificationPanel;
  }

  public VcsDependentHistoryComponents(ColumnInfo<?, ?>[] columns, @Nullable Consumer<VcsFileRevision> revisionListener, @Nullable JComponent detailsComponent) {
    this(columns, revisionListener, detailsComponent, null);
  }

  public static @NotNull VcsDependentHistoryComponents createOnlyColumns(ColumnInfo @NotNull [] columns) {
    return new VcsDependentHistoryComponents(columns, null, null, null);
  }

  public ColumnInfo[] getColumns() {
    return myColumns;
  }

  public @Nullable Consumer<VcsFileRevision> getRevisionListener() {
    return myRevisionListener;
  }

  public @Nullable JComponent getDetailsComponent() {
    return myDetailsComponent;
  }

  public @Nullable EditorNotificationPanel getNotificationPanel() {
    return myNotificationPanel;
  }
}
