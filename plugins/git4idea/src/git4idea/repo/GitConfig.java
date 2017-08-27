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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

  /**
 * Reads information from the {@code .git/config} file, and parses it to actual objects.
 * <p/>
 * Currently doesn't read all the information: just general information about remotes and branch tracking.
 * <p/>
 * Parsing is performed with the help of <a href="http://ini4j.sourceforge.net/">ini4j</a> library.
 *
 * TODO: note, that other git configuration files (such as ~/.gitconfig) are not handled yet.
 */
public class GitConfig {

  private static final Logger LOG = Logger.getInstance(GitConfig.class);

  private static final Pattern REMOTE_SECTION = Pattern.compile("(?:svn-)?remote \"(.*)\"");
  private static final Pattern URL_SECTION = Pattern.compile("url \"(.*)\"");
  private static final Pattern BRANCH_INFO_SECTION = Pattern.compile("branch \"(.*)\"");
  private static final Pattern BRANCH_COMMON_PARAMS_SECTION = Pattern.compile("branch");

  @NotNull private final Collection<Remote> myRemotes;
  @NotNull private final Collection<Url> myUrls;
  @NotNull private final Collection<BranchConfig> myTrackedInfos;


  private GitConfig(@NotNull Collection<Remote> remotes, @NotNull Collection<Url> urls, @NotNull Collection<BranchConfig> trackedInfos) {
    myRemotes = remotes;
    myUrls = urls;
    myTrackedInfos = trackedInfos;
  }

  /**
   * <p>Returns Git remotes defined in {@code .git/config}.</p>
   *
   * <p>Remote is returned with all transformations (such as {@code pushUrl, url.<base>.insteadOf}) already applied to it.
   *    See {@link GitRemote} for details.</p>
   *
   * <p><b>Note:</b> remotes can be defined separately in {@code .git/remotes} directory, by creating a file for each remote with
   *    remote parameters written in the file. This method returns ONLY remotes defined in {@code .git/config}.</p>
   * @return Git remotes defined in {@code .git/config}.
   */
  @NotNull
  Collection<GitRemote> parseRemotes() {
    // populate GitRemotes with substituting urls when needed
    return ContainerUtil.map(myRemotes, remote -> convertRemoteToGitRemote(myUrls, remote));
  }

  @NotNull
  private static GitRemote convertRemoteToGitRemote(@NotNull Collection<Url> urls, @NotNull Remote remote) {
    UrlsAndPushUrls substitutedUrls = substituteUrls(urls, remote);
    return new GitRemote(remote.name, substitutedUrls.getUrls(), substitutedUrls.getPushUrls(),
                         remote.getFetchSpecs(), remote.getPushSpecs());
  }

  /**
   * Create branch tracking information based on the information defined in {@code .git/config}.
   */
  @NotNull
  Collection<GitBranchTrackInfo> parseTrackInfos(@NotNull final Collection<GitLocalBranch> localBranches,
                                                 @NotNull final Collection<GitRemoteBranch> remoteBranches) {
    return ContainerUtil.mapNotNull(myTrackedInfos, config -> convertBranchConfig(config, localBranches, remoteBranches));
  }

  /**
   * Creates an instance of GitConfig by reading information from the specified {@code .git/config} file.
   * <p/>
   * If some section is invalid, it is skipped, and a warning is reported.
   */
  @NotNull
  static GitConfig read(@NotNull File configFile) {
    GitConfig emptyConfig = new GitConfig(emptyList(), emptyList(), emptyList());
    if (!configFile.exists()) {
      LOG.info("No .git/config file at " + configFile.getPath());
      return emptyConfig;
    }

    Stream<String> lines;
    try {
      lines = Files.lines(configFile.toPath());
    }
    catch (IOException e) {
      return emptyConfig;
    }

    final List<Remote> remotes = new ArrayList<>();
    final List<Url> urls = new ArrayList<>();
    final List<BranchConfig> branches = new ArrayList<>();

    List<String> lineList = lines.map(s -> s.trim())
      .filter(s -> !s.isEmpty())
      .filter(s -> !s.matches("^(#|;).*"))
      .map(s -> {
        String clone = s;
        clone = clone.replaceAll("\"[^\"]*\"", "");
        Matcher commentMatcher = Pattern.compile("[#;].*$").matcher(clone);
        if (commentMatcher.matches()) {
          return s.substring(0, s.lastIndexOf(commentMatcher.group()));
        }
        else {
          return s;
        }
      })
      .collect(Collectors.toList());

    Pattern sectionPattern = Pattern.compile("^\\[([A-Za-z0-9-\\.]+)(\\s+\"(.*)\")?\\]$");
    Pattern variablePattern = Pattern.compile("^([A-Za-z0-9-]+)\\s*=\\s*(.*)$");

    Section currentSection = null;
    for (String line : lineList) {
      Matcher sectionMatcher = sectionPattern.matcher(line);
      if (sectionMatcher.matches()) {
        String section = sectionMatcher.group(1).toLowerCase(Locale.getDefault());
        String subsection = sectionMatcher.group(3);
        switch (section) {
          case "remote":
          case "svn-remote":
            Remote remote = new Remote(subsection);
            if (subsection != null) {
              // Test GitConfigTest.test_remote_unspecified_section() requires the default remote section not to be included
              remotes.add(remote);
            }
            currentSection = remote;
            break;
          case "branch":
            BranchConfig branch = new BranchConfig(subsection);
            branches.add(branch);
            currentSection = branch;
            break;
          case "url":
            Url url = new Url(subsection);
            urls.add(url);
            currentSection = url;
            break;
          default:
            LOG.info("Section '" + section + "' not supported yet.");
            currentSection = new Section() {
            };
        }
      }
      else {
        Matcher variableMatcher = variablePattern.matcher(line);
        if (variableMatcher.matches()) {
          String name = variableMatcher.group(1).toLowerCase(Locale.getDefault());
          String value = variableMatcher.group(2);
          if (currentSection == null) {
            LOG.warn("Invalid git config file. Starts with variables without section header.");
            return emptyConfig;
          }
          currentSection.addVariable(name, value);
        }
      }
    }

    return new GitConfig(remotes, urls, branches);
  }

  @Nullable
  private static GitBranchTrackInfo convertBranchConfig(@Nullable BranchConfig branchConfig,
                                                        @NotNull Collection<GitLocalBranch> localBranches,
                                                        @NotNull Collection<GitRemoteBranch> remoteBranches) {
    if (branchConfig == null) {
      return null;
    }
    final String branchName = branchConfig.getName();
    String remoteName = branchConfig.getRemote();
    String mergeName = branchConfig.getMerge();
    String rebaseName = branchConfig.getRebase();

    if (StringUtil.isEmptyOrSpaces(mergeName) && StringUtil.isEmptyOrSpaces(rebaseName)) {
      LOG.debug("No branch." + branchName + ".merge/rebase item in the .git/config");
      return null;
    }
    if (StringUtil.isEmptyOrSpaces(remoteName)) {
      LOG.debug("No branch." + branchName + ".remote item in the .git/config");
      return null;
    }

    boolean merge = mergeName != null;
    final String remoteBranchName = StringUtil.unquoteString(merge ? mergeName : rebaseName);

    GitLocalBranch localBranch = findLocalBranch(branchName, localBranches);
    GitRemoteBranch remoteBranch = findRemoteBranch(remoteBranchName, remoteName, remoteBranches);
    if (localBranch == null || remoteBranch == null) {
      // obsolete record in .git/config: local or remote branch doesn't exist, but the tracking information wasn't removed
      LOG.debug("localBranch: " + localBranch + ", remoteBranch: " + remoteBranch);
      return null;
    }
    return new GitBranchTrackInfo(localBranch, remoteBranch, merge);
  }

  @Nullable
  private static GitLocalBranch findLocalBranch(@NotNull String branchName, @NotNull Collection<GitLocalBranch> localBranches) {
    final String name = GitBranchUtil.stripRefsPrefix(branchName);
    return ContainerUtil.find(localBranches, input -> input.getName().equals(name));
  }

  @Nullable
  public static GitRemoteBranch findRemoteBranch(@NotNull String remoteBranchName,
                                                 @NotNull String remoteName,
                                                 @NotNull Collection<GitRemoteBranch> remoteBranches) {
    final String branchName = GitBranchUtil.stripRefsPrefix(remoteBranchName);
    return ContainerUtil.find(remoteBranches, branch -> branch.getNameForRemoteOperations().equals(branchName) &&
                                                        branch.getRemote().getName().equals(remoteName));
  }

  /**
   * <p>
   *   Applies {@code url.<base>.insteadOf} and {@code url.<base>.pushInsteadOf} transformations to {@code url} and {@code pushUrl} of
   *   the given remote.
   * </p>
   * <p>
   *   The logic, is as follows:
   *   <ul>
   *     <li>If remote.url starts with url.insteadOf, it it substituted.</li>
   *     <li>If remote.pushUrl starts with url.insteadOf, it is substituted.</li>
   *     <li>If remote.pushUrl starts with url.pushInsteadOf, it is not substituted.</li>
   *     <li>If remote.url starts with url.pushInsteadOf, but remote.pushUrl is given, additional push url is not added.</li>
   *   </ul>
   * </p>
   *
   * <p>
   *   TODO: if there are several matches in url sections, the longest should be applied. // currently only one is applied
   * </p>
   *
   * <p>
   *   This is according to {@code man git-config ("url.<base>.insteadOf" and "url.<base>.pushInsteadOf" sections},
   *   {@code man git-push ("URLS" section)} and the following discussions in the Git mailing list:
   *   <a href="http://article.gmane.org/gmane.comp.version-control.git/183587">insteadOf override urls and pushUrls</a>,
   *   <a href="http://thread.gmane.org/gmane.comp.version-control.git/127910">pushInsteadOf doesn't override explicit pushUrl</a>.
   * </p>
   */
  @NotNull
  private static UrlsAndPushUrls substituteUrls(@NotNull Collection<Url> urlSections, @NotNull Remote remote) {
    List<String> urls = new ArrayList<>(remote.getUrls().size());
    Collection<String> pushUrls = new ArrayList<>();

    // urls are substituted by insteadOf
    // if there are no pushUrls, we create a pushUrl for pushInsteadOf substitutions
    for (final String remoteUrl : remote.getUrls()) {
      boolean substituted = false;
      for (Url url : urlSections) {
        String insteadOf = url.getInsteadOf();
        String pushInsteadOf = url.getPushInsteadOf();
        // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
        if (insteadOf != null && remoteUrl.startsWith(insteadOf)) {
          urls.add(substituteUrl(remoteUrl, url, insteadOf));
          substituted = true;
          break;
        }
        else if (pushInsteadOf != null && remoteUrl.startsWith(pushInsteadOf)) {
          if (remote.getPushUrls().isEmpty()) { // only if there are no explicit pushUrls
              pushUrls.add(substituteUrl(remoteUrl, url, pushInsteadOf)); // pushUrl is different
          }
          urls.add(remoteUrl);                                             // but url is left intact
          substituted = true;
          break;
        }
      }
      if (!substituted) {
        urls.add(remoteUrl);
      }
    }

    // pushUrls are substituted only by insteadOf, not by pushInsteadOf
    for (final String remotePushUrl : remote.getPushUrls()) {
      boolean substituted = false;
      for (Url url : urlSections) {
        String insteadOf = url.getInsteadOf();
        // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
        if (insteadOf != null && remotePushUrl.startsWith(insteadOf)) {
          pushUrls.add(substituteUrl(remotePushUrl, url, insteadOf));
          substituted = true;
          break;
        }
      }
      if (!substituted) {
        pushUrls.add(remotePushUrl);
      }
    }

    // if no pushUrls are explicitly defined yet via pushUrl or url.<base>.pushInsteadOf, they are the same as urls.
    if (pushUrls.isEmpty()) {
      pushUrls = new ArrayList<>(urls);
    }

    return new UrlsAndPushUrls(urls, pushUrls);
  }

  @NotNull
  private static String substituteUrl(@NotNull String remoteUrl, @NotNull Url url, @NotNull String insteadOf) {
    return url.name + remoteUrl.substring(insteadOf.length());
  }

  interface Section {
    default void addVariable(String name, String value) {
      LOG.info("Variable '" + name + "' ignored.");
    }
  }

  static private class Remote implements Section {
    private String name;
    private List<String> fetch = new ArrayList<>(2);
    private List<String> push = new ArrayList<>(2);
    private List<String> url = new ArrayList<>(2);
    private List<String> pushUrl = new ArrayList<>(2);

    public Remote(String newName) {
      if (newName != null) {
        name = newName;
      }
      else {
        name = "";
      }
    }

    public String getName() {
      return name;
    }

    @Override
    public void addVariable(String name, String value) {
      switch (name) {
        case "fetch":
          fetch.add(value);
          break;
        case "push":
          push.add(value);
          break;
        case "url":
          url.add(value);
          break;
        case "pushurl":
          pushUrl.add(value);
          break;
        default:
          LOG.info("Unknown variable not added to remote config: " + name);
      }
    }

    @Nullable
    List<String> getFetchSpecs() {
      return fetch;
    }

    @Nullable
    List<String> getPushSpecs() {
      return push;
    }

    @Nullable
    List<String> getUrls() {
      return url;
    }

    @Nullable
    List<String> getPushUrls() {
      return pushUrl;
    }
  }

  private static class Url implements Section {
    private String name;
    private String insteadOf;
    private String pushInsteadOf;

    public Url(String newName) {
      name = newName;
    }

    public String getName() {
      return name;
    }

    @Override
    public void addVariable(String name, String value) {
      switch (name) {
        case "insteadof":
          insteadOf = value;
          break;
        case "pushinsteadof":
          pushInsteadOf = value;
          break;
        default:
          LOG.info("Unsupported variable '" + name + "' ignored in url section.");
      }
    }

    @Nullable
    String getInsteadOf() {
      return insteadOf;
    }

    @Nullable
    String getPushInsteadOf() {
      return pushInsteadOf;
    }
  }

  private static class BranchConfig implements Section {
    private String name;
    private String remote;
    private String merge;
    private String rebase;

    public BranchConfig(String newName) {
      name = newName;
    }

    public String getName() {
      return name;
    }

    @Override
    public void addVariable(String name, String value) {
      switch (name) {
        case "remote":
          remote = value;
          break;
        case "merge":
          merge = value;
          break;
        case "rebase":
          rebase = value;
          break;
        default:
          LOG.info("Unsupported variable '" + name + "' ignorde in branch section.");
      }
    }

    @Nullable
    String getRemote() {
      return remote;
    }

    @Nullable
    String getMerge() {
      return merge;
    }

    @Nullable
    String getRebase() {
      return rebase;
    }
  }

  private static class UrlsAndPushUrls {
    final List<String> myUrls;
    final Collection<String> myPushUrls;

    private UrlsAndPushUrls(List<String> urls, Collection<String> pushUrls) {
      myPushUrls = pushUrls;
      myUrls = urls;
    }

    public Collection<String> getPushUrls() {
      return myPushUrls;
    }

    public List<String> getUrls() {
      return myUrls;
    }
  }

  @NotNull
  private static String[] notNull(@Nullable String[] s) {
    return s == null ? ArrayUtil.EMPTY_STRING_ARRAY : s;
  }

  @NotNull
  private static Collection<String> nonNullCollection(@Nullable String[] array) {
    return array == null ? emptyList() : new ArrayList<>(asList(array));
  }

}
