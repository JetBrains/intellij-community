/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.repo;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * <p>
 *   Holds information about a remote in Git repository.
 * </p>
 *
 * <p>
 *   Git remote as defined in {@code .git/config} may contain url(s) and pushUrl(s). <br/>
 *   If no pushUrl is given, then url is used to fetch and to push. Otherwise url is used to fetch, pushUrl is used to push.
 *   If there are several urls and no pushUrls, then 1 url is used to fetch (because it is not possible and makes no sense to fetch
 *   several urls at once), and all urls are used to push. If there are several urls and at least one pushUrl, then only pushUrl(s)
 *   are used to push.
 *   There are also some rules about url substitution, like {@code url.<base>.insteadOf}.
 * </p>
 * <p>
 *   GitRemote instance constructed by {@link GitConfig#read(java.io.File)} has all these rules applied.
 *   Thus, for example, if only one {@code url} and no {@code pushUrls} are defined for the remote, 
 *   both {@link #getUrls()} and {@link #getPushUrls()} will return this url. <br/>
 *   This is made to avoid urls transformation logic from the code using GitRemote, leaving it all in GitConfig parsing.
 * </p>
 * <p>
 *   Same applies to fetch and push specs: {@link #getPushRefSpec()} returns the spec,
 *   even if there are no separate record in {@code .git/config}
 * </p>
 * 
 * <p>
 *   NB: Not all remote preferences (defined in {@code .git/config} are stored in the object.
 *   If some additional data is needed, add the field, getter, constructor parameter and populate it in {@link GitConfig}.
 * </p>
 * 
 * <p>
 *   This class implements {@link Comparable}, but remotes are compared only by their names 
 *   (which is inconsistent with {@code equals}, but is fair enough, since there can't be different remotes with the same name). 
 * </p>
 * 
 * @author Kirill Likhodedov
 */
public final class GitRemote implements Comparable<GitRemote> {

  private final String myName;
  private final Collection<String> myUrls;
  private final Collection<String> myPushUrls;
  private final String myFetchRefSpec;
  private final String myPushRefSpec;

  GitRemote(@NotNull String name, @NotNull Collection<String> urls, @NotNull Collection<String> pushUrls, @NotNull String fetchRefSpec, @NotNull String pushRefSpec) {
    myName = name;
    myUrls = urls;
    myPushUrls = pushUrls;
    myFetchRefSpec = fetchRefSpec;
    myPushRefSpec = pushRefSpec;
  }

  public String getName() {
    return myName;
  }

  public Collection<String> getUrls() {
    return myUrls;
  }

  public Collection<String> getPushUrls() {
    return myPushUrls;
  }

  public String getFetchRefSpec() {
    return myFetchRefSpec;
  }

  public String getPushRefSpec() {
    return myPushRefSpec;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GitRemote gitRemote = (GitRemote)o;

    if (!myFetchRefSpec.equals(gitRemote.myFetchRefSpec)) return false;
    if (!myName.equals(gitRemote.myName)) return false;
    if (!myPushRefSpec.equals(gitRemote.myPushRefSpec)) return false;
    if (!myPushUrls.equals(gitRemote.myPushUrls)) return false;
    if (!myUrls.equals(gitRemote.myUrls)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myName.hashCode();
    result = 31 * result + myUrls.hashCode();
    result = 31 * result + myPushUrls.hashCode();
    result = 31 * result + myFetchRefSpec.hashCode();
    result = 31 * result + myPushRefSpec.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return String.format("GitRemote{myName='%s', myUrls=%s, myPushUrls=%s, myFetchRefSpec='%s', myPushRefSpec='%s'}",
                         myName, myUrls, myPushUrls, myFetchRefSpec, myPushRefSpec);
  }

  @Override
  public int compareTo(@NotNull GitRemote o) {
    return getName().compareTo(o.getName());
  }
}
