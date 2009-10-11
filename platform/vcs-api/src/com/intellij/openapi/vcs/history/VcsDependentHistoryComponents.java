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

import com.intellij.util.Consumer;
import com.intellij.util.ui.ColumnInfo;

import javax.swing.*;

public class VcsDependentHistoryComponents {
  private final ColumnInfo[] myColumns;
  private final Consumer<VcsFileRevision> myRevisionListener;
  private final JComponent myDetailsComponent;

  public VcsDependentHistoryComponents(final ColumnInfo[] columns, final Consumer<VcsFileRevision> revisionListener, final JComponent detailsComponent) {
    myColumns = columns;
    myRevisionListener = revisionListener;
    myDetailsComponent = detailsComponent;
  }

  public static VcsDependentHistoryComponents createOnlyColumns(final ColumnInfo[] columns) {
    return new VcsDependentHistoryComponents(columns, null, null);
  }

  public ColumnInfo[] getColumns() {
    return myColumns;
  }

  public Consumer<VcsFileRevision> getRevisionListener() {
    return myRevisionListener;
  }

  public JComponent getDetailsComponent() {
    return myDetailsComponent;
  }
}
