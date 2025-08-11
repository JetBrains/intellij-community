// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class LocalChangeList implements Cloneable, ChangeList {
  public static final @NonNls String OLD_DEFAULT_NAME = "Default";

  public static LocalChangeList createEmptyChangeList(Project project, @NotNull String name) {
    return VcsContextFactory.getInstance().createLocalChangeList(project, name);
  }

  public static List<String> getAllDefaultNames() {
    return Arrays.asList(VcsBundle.message("changes.default.changelist.name"),
                         VcsBundle.message("changes.default.changelist.name.old"),
                         OLD_DEFAULT_NAME);
  }

  @Override
  public abstract Collection<Change> getChanges();

  /**
   * Logical id that identifies the changelist and should survive name change.
   */
  public @NotNull @NonNls String getId() {
    return getName();
  }

  @Override
  public abstract @NotNull @Nls String getName();

  @Override
  public abstract @Nullable @NlsSafe String getComment();

  public abstract boolean isDefault();

  public abstract boolean isReadOnly();

  /**
   * Get additional data associated with this changelist.
   */
  public abstract @Nullable Object getData();

  public abstract LocalChangeList copy();

  public boolean hasDefaultName() {
    return getAllDefaultNames().contains(getName());
  }

  public boolean isBlank() {
    return hasDefaultName() && getData() == null;
  }

  /**
   * @deprecated use {@link ChangeListManager#editComment}
   */
  @Deprecated(forRemoval = true)
  public abstract void setComment(@Nullable String comment);

  public static @NotNull @NlsSafe String getDefaultName() {
    return VcsBundle.message("changes.default.changelist.name");
  }
}
