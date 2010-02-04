/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;

import java.util.Comparator;

public class ChangesComparator implements Comparator<Change> {
  private static final ChangesComparator ourInstance = new ChangesComparator();

  public static ChangesComparator getInstance() {
    return ourInstance;
  }

  private ChangesComparator() {
  }

  public int compare(final Change o1, final Change o2) {
    return ChangesUtil.getFilePath(o1).getName().compareToIgnoreCase(ChangesUtil.getFilePath(o2).getName());
  }
}
