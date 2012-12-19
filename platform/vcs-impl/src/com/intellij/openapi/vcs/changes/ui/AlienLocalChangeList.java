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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlienLocalChangeList extends LocalChangeList {
  private final List<Change> myChanges;
  private String myName;
  private String myComment;

  public AlienLocalChangeList(final List<Change> changes, final String name) {
    myChanges = changes;
    myName = name;
    myComment = "";
  }

  public Collection<Change> getChanges() {
    return myChanges;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull final String name) {
    myName = name;
  }

  public String getComment() {
    return myComment;
  }

  public void setComment(final String comment) {
    myComment = comment;
  }

  public boolean isDefault() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public void setReadOnly(final boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Object getData() {
    throw new UnsupportedOperationException();
  }

  public LocalChangeList copy() {
    throw new UnsupportedOperationException();
  }

  public static final AlienLocalChangeList DEFAULT_ALIEN = new AlienLocalChangeList(Collections.<Change>emptyList(), "Default") {
    public boolean isDefault() {
      return true;
    }
  };
}
