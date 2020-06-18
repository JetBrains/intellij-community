// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class LocalChangeList implements Cloneable, ChangeList {
  @NonNls public static final String OLD_DEFAULT_NAME = "Default";

  public static LocalChangeList createEmptyChangeList(Project project, @NotNull String name) {
    return VcsContextFactory.SERVICE.getInstance().createLocalChangeList(project, name);
  }

  @Override
  public abstract Collection<Change> getChanges();

  /**
   * Logical id that identifies the changelist and should survive name changing.
   */
  @NotNull
  public String getId() {
    return getName();
  }

  @Override
  @NotNull
  public abstract String getName();

  @Override
  @Nullable
  public abstract String getComment();

  public abstract boolean isDefault();

  public abstract boolean isReadOnly();

  /**
   * Get additional data associated with this changelist.
   */
  @Nullable
  public abstract Object getData();

  public abstract LocalChangeList copy();

  public boolean hasDefaultName() {
    return getDefaultName().equals(getName()) || OLD_DEFAULT_NAME.equals(getName());
  }

  public boolean isBlank() {
    return hasDefaultName() && getData() == null;
  }

  /**
   * @deprecated use {@link ChangeListManager#editName}
   */
  @Deprecated
  public abstract void setName(@NotNull String name);

  /**
   * @deprecated use {@link ChangeListManager#editComment}
   */
  @Deprecated
  public abstract void setComment(@Nullable String comment);

  /**
   * @deprecated use {@link ChangeListManager#setReadOnly}
   */
  @Deprecated
  public abstract void setReadOnly(boolean isReadOnly);

  public static @NotNull String getDefaultName() {
    return VcsBundle.message("changes.default.changelist.name");
  }
}
