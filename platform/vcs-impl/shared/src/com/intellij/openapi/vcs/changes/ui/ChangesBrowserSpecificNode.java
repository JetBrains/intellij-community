// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public abstract class ChangesBrowserSpecificNode<T, F> extends ChangesBrowserNode<T> {
  @ApiStatus.Internal
  public static final String MANY_FILES_LIMIT_KEY = "vcs.unversioned.files.max.intree";
  @ApiStatus.Internal
  public static final int MANY_FILES_LIMIT_DEFAULT = 1000;

  protected final boolean myIsMany;
  protected final @NotNull Runnable myDialogShower;
  private final int myManyFileCount;
  private final int myManyDirectoryCount;

  private SimpleTextAttributes myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

  protected ChangesBrowserSpecificNode(@NotNull T userObject, @NotNull Collection<F> files, int manyDirectoryCount, @NotNull Runnable shower) {
    this(userObject, files.size() - manyDirectoryCount, manyDirectoryCount, isManyFiles(files), shower);
  }

  @ApiStatus.Internal
  protected ChangesBrowserSpecificNode(@NotNull T userObject, int fileCount, int directoryCount, @NotNull Runnable shower) {
    this(userObject, fileCount, directoryCount, true, shower);
  }

  private ChangesBrowserSpecificNode(@NotNull T userObject, int fileCount, int directoryCount, boolean isMany, @NotNull Runnable shower) {
    super(userObject);
    myManyDirectoryCount = directoryCount;
    myManyFileCount = fileCount;
    myIsMany = isMany;
    myDialogShower = shower;
  }

  public final void setAttributes(@NotNull SimpleTextAttributes attributes) {
    myAttributes = attributes;
  }

  @Override
  public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
    renderer.append(getTextPresentation(), myAttributes);
    appendCount(renderer);

    if (isManyFiles()) {
      renderer.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append(VcsBundle.message("changes.browse"), SimpleTextAttributes.LINK_ATTRIBUTES, myDialogShower);
    }
  }

  @Override
  public int getFileCount() {
    return myIsMany ? myManyFileCount : super.getFileCount();
  }

  @Override
  public int getDirectoryCount() {
    return myIsMany ? myManyDirectoryCount : super.getDirectoryCount();
  }

  public boolean isManyFiles() {
    return myIsMany;
  }

  public static boolean isManyFiles(@NotNull Collection<?> files) {
    return files.size() > Registry.intValue(MANY_FILES_LIMIT_KEY, MANY_FILES_LIMIT_DEFAULT);
  }
}
