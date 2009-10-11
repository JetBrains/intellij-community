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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IssueNavigationLink {
  private String myIssueRegexp = "";
  private String myLinkRegexp = "";
  private Pattern myIssuePattern;

  public IssueNavigationLink() {
  }

  public IssueNavigationLink(@NonNls final String issueRegexp, @NonNls final String linkRegexp) {
    myIssueRegexp = issueRegexp;
    myLinkRegexp = linkRegexp;
  }

  @NotNull
  public String getIssueRegexp() {
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

  @NotNull
  public String getLinkRegexp() {
    return myLinkRegexp;
  }

  public void setLinkRegexp(final String linkRegexp) {
    myLinkRegexp = linkRegexp;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IssueNavigationLink that = (IssueNavigationLink)o;

    if (!myIssueRegexp.equals(that.myIssueRegexp)) return false;
    if (!myLinkRegexp.equals(that.myLinkRegexp)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myIssueRegexp.hashCode();
    result = 31 * result + myLinkRegexp.hashCode();
    return result;
  }
}