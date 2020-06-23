// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks;

import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
@Tag("changelist")
public class ChangeListInfo {

  @Attribute("id")
  public String id;

  @Attribute("name")
  public String name;

  @Attribute("comment")
  public String comment;

  /** For serialization */
  @SuppressWarnings({"UnusedDeclaration"})
  public ChangeListInfo() {
  }

  public ChangeListInfo(@NotNull LocalChangeList changeList) {
    id = changeList.getId();
    name = changeList.getName();
    comment = changeList.getComment();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ChangeListInfo)) return false;

    ChangeListInfo that = (ChangeListInfo)o;

    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}
