// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public abstract class ChangesBrowserSpecificNode<T, F> extends ChangesBrowserNode<T> {
  protected final boolean myIsMany;
  @NotNull protected final Runnable myDialogShower;
  private final int myManyFileCount;
  private final int myManyDirectoryCount;

  private SimpleTextAttributes myAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;

  protected ChangesBrowserSpecificNode(@NotNull T userObject, @NotNull Collection<F> files, int manyDirectoryCount, @NotNull Runnable shower) {
    super(userObject);
    // if files presented in the same view recalculate number of dirs and files -> provide -1; otherwise use from model
    myManyDirectoryCount = manyDirectoryCount;
    myManyFileCount = files.size() - myManyDirectoryCount;
    myIsMany = isManyFiles(files);
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
    return files.size() > Registry.intValue("vcs.unversioned.files.max.intree", 1000);
  }
}
