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

  public String toPresentableString() {
    return myProviderLocation.toPresentableString();
  }

  public CommittedChangesProvider getProvider() {
    return myProvider;
  }

  public RepositoryLocation getProviderLocation() {
    return myProviderLocation;
  }

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
