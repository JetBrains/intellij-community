/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
public class MockChangeList extends LocalChangeList {

  Collection<Change> myChanges = new ArrayList<>();
  private final String myName;

  public MockChangeList(String name) {
    myName = name;
  }

  public void add(Change change) {
    myChanges.add(change);
  }

  @Override
  public Collection<Change> getChanges() {
    return myChanges;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getComment() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setComment(String comment) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isDefault() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isReadOnly() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setReadOnly(boolean isReadOnly) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public Object getData() {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList copy() {
    throw new UnsupportedOperationException();
  }
}
