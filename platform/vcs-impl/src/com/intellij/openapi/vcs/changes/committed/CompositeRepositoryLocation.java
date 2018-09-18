// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;

/**
 * @author yole
 */
class CompositeRepositoryLocation implements RepositoryLocation {
  private final CommittedChangesProvider myProvider;
  private final RepositoryLocation myProviderLocation;

  public CompositeRepositoryLocation(final CommittedChangesProvider provider, final RepositoryLocation providerLocation) {
    myProvider = provider;
    myProviderLocation = providerLocation;
  }

  public String toString() {
    return myProviderLocation.toString();
  }

  @Override
  public String toPresentableString() {
    return myProviderLocation.toPresentableString();
  }

  public CommittedChangesProvider getProvider() {
    return myProvider;
  }

  public RepositoryLocation getProviderLocation() {
    return myProviderLocation;
  }

  @Override
  public String getKey() {
    return myProviderLocation.getKey();
  }

  @Override
  public void onBeforeBatch() throws VcsException {
    myProviderLocation.onBeforeBatch();
  }

  @Override
  public void onAfterBatch() {
    myProviderLocation.onAfterBatch();
  }
}
