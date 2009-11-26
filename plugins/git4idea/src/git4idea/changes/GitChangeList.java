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
package git4idea.changes;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Git change list
 */
public class GitChangeList extends LocalChangeList {
  private String name;
  private String comment;
  private final Collection<Change> changes;

  public GitChangeList(@NotNull String name, String comment, Collection<Change> changes) {
    super();
    setName(name);
    setComment(comment);
    this.changes = changes;
  }

  @NotNull
  public String getName() {
    return name;
  }

  public void setName(@NotNull String name) {
    this.name = name;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public boolean isDefault() {
    return true;
  }

  public boolean isReadOnly() {
    return true;
  }

  public void setReadOnly(boolean isReadOnly) {
  }

  public Collection<Change> getChanges() {
    return changes;
  }

  public LocalChangeList copy() {
    return new GitChangeList(name, comment, changes);
  }
}
