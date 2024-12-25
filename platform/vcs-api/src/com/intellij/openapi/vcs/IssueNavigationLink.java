// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;


public class IssueNavigationLink {
  private String myIssueRegexp = "";
  private String myLinkRegexp = "";
  private Pattern myIssuePattern;

  public IssueNavigationLink() {
  }

  public IssueNavigationLink(final @NonNls String issueRegexp, final @NonNls String linkRegexp) {
    myIssueRegexp = issueRegexp;
    myLinkRegexp = linkRegexp;
  }

  public @NotNull String getIssueRegexp() {
    return myIssueRegexp;
  }

  public void setIssueRegexp(final String issueRegexp) {
    myIssueRegexp = issueRegexp;
    myIssuePattern = null;
  }

  public Pattern getIssuePattern() {
    if (myIssuePattern == null) {
      myIssuePattern = Pattern.compile(myIssueRegexp);
    }
    return myIssuePattern;
  }

  public @NotNull String getLinkRegexp() {
    return myLinkRegexp;
  }

  public void setLinkRegexp(final String linkRegexp) {
    myLinkRegexp = linkRegexp;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IssueNavigationLink that = (IssueNavigationLink)o;

    if (!myIssueRegexp.equals(that.myIssueRegexp)) return false;
    if (!myLinkRegexp.equals(that.myLinkRegexp)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = myIssueRegexp.hashCode();
    result = 31 * result + myLinkRegexp.hashCode();
    return result;
  }

  @Override
  public @NonNls String toString() {
    return "IssueNavigationLink{" +
           "myIssueRegexp='" + myIssueRegexp + '\'' +
           ", myLinkRegexp='" + myLinkRegexp + '\'' +
           '}';
  }
}