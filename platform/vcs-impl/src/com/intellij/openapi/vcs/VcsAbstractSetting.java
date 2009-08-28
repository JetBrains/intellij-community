package com.intellij.openapi.vcs;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;


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
    myApplicable.add(vcs);
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
