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
package com.intellij.openapi.vcs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;


public class VcsAbstractSetting {
  protected final String myDisplayName;
  private final Collection<AbstractVcs> myApplicable = new HashSet<AbstractVcs>();

  protected VcsAbstractSetting(final String displayName) {
    myDisplayName = displayName;
  }

  public String getDisplayName(){
    return myDisplayName;
  }

  public void addApplicableVcs(AbstractVcs vcs) {
    if (vcs != null) {
      myApplicable.add(vcs);
    }
  }

  public boolean isApplicableTo(Collection<AbstractVcs> vcs) {
    for (AbstractVcs abstractVcs : vcs) {
      if (myApplicable.contains(abstractVcs)) return true;
    }
    return false;
  }

  public List<AbstractVcs> getApplicableVcses() {
    return new ArrayList<AbstractVcs>(myApplicable);
  }
}
