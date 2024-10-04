// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class MockAbstractVcs extends AbstractVcs {
  private static final String NAME = "mock"; // NON-NLS
  private static final VcsKey ourKey = createKey(NAME);
  private CheckinEnvironment myCheckinEnvironment;
  private CommittedChangesProvider myCommittedChangesProvider;
  private DiffProvider myDiffProvider;
  private ChangeProvider myChangeProvider;
  private boolean myAllowNestedRoots;

  public MockAbstractVcs(@NotNull Project project){
    super(project, NAME);
    myAllowNestedRoots = false;
  }

  public MockAbstractVcs(@NotNull Project project, final String name) {
    super(project, name);
  }

  @Override
  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangesProvider;
  }

  @Override
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return getName(); //NON-NLS
  }

  @Override
  public ChangeProvider getChangeProvider() {
    return myChangeProvider;
  }

  @Override
  public void setCheckinEnvironment(CheckinEnvironment ce) {
    myCheckinEnvironment = ce;
  }

  public void setCommittedChangesProvider(final CommittedChangesProvider committedChangesProvider) {
    myCommittedChangesProvider = committedChangesProvider;
  }

  public void setDiffProvider(final DiffProvider diffProvider) {
    myDiffProvider = diffProvider;
  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(final String revisionNumberString) {
    return new VcsRevisionNumber.Int(Integer.parseInt(revisionNumberString));
  }

  @Override
  public String getRevisionPattern() {
    return ourIntegerPattern;
  }

  public void setChangeProvider(final ChangeProvider changeProvider) {
    myChangeProvider = changeProvider;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public boolean allowsNestedRoots() {
    return myAllowNestedRoots;
  }

  public void setAllowNestedRoots(boolean allowNestedRoots) {
    myAllowNestedRoots = allowNestedRoots;
  }
}
