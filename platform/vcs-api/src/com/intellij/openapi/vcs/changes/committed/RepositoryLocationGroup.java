// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RepositoryLocationGroup implements RepositoryLocation {
  @NotNull
  private final String myPresentableString;
  private final List<RepositoryLocation> myLocations;

  public RepositoryLocationGroup(@NonNls @NotNull String presentableString) {
    myPresentableString = presentableString;
    myLocations = new ArrayList<>();
  }

  @Override
  @NonNls
  @NotNull
  public String toPresentableString() {
    return myPresentableString;
  }

  public void add(@NotNull final RepositoryLocation location) {
    for (int i = 0; i < myLocations.size(); i++) {
      final RepositoryLocation t = myLocations.get(i);
      if (t.getKey().compareTo(location.getKey()) >= 0) {
        myLocations.add(i, location);
        return;
      }
    }
    myLocations.add(location);
  }

  @Override
  @NonNls
  public String getKey() {
    final StringBuilder sb = new StringBuilder(myPresentableString);
    // they are ordered
    for (RepositoryLocation location : myLocations) {
      sb.append(location.getKey());
    }
    return sb.toString();
  }

  public List<RepositoryLocation> getLocations() {
    return myLocations;
  }
}
