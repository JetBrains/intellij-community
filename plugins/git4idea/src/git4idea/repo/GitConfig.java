// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads information from the {@code .git/config} file, and parses it to actual objects.
 * <p/>
 * Currently doesn't read all the information: just general information about remotes and branch tracking.
 * <p/>
 * Parsing is performed with the help of <a href="http://ini4j.sourceforge.net/">ini4j</a> library.
 *
 * TODO: note, that other git configuration files (such as ~/.gitconfig) are not handled yet.
 */
@ApiStatus.Internal
public final class GitConfig {
  private static final Logger LOG = Logger.getInstance(GitConfig.class);

  private static final Pattern REMOTE_SECTION = Pattern.compile("(?:svn-)?remote \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern URL_SECTION = Pattern.compile("url \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern BRANCH_INFO_SECTION = Pattern.compile("branch \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern BRANCH_COMMON_PARAMS_SECTION = Pattern.compile("branch", Pattern.CASE_INSENSITIVE);
  private static final String CORE_SECTION = "core";

  private final @NotNull Collection<Remote> myRemotes;
  private final @NotNull Collection<Url> myUrls;
  private final @NotNull Collection<BranchConfig> myTrackedInfos;
  private final @NotNull Core myCore;

  private GitConfig(@NotNull Collection<Remote> remotes,
                    @NotNull Collection<Url> urls,
                    @NotNull Collection<BranchConfig> trackedInfos,
                    @NotNull Core core) {
    myRemotes = remotes;
    myUrls = urls;
    myTrackedInfos = trackedInfos;
    myCore = core;
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
  @VisibleForTesting
  public @NotNull Set<GitRemote> parseRemotes() {
    // populate GitRemotes with substituting urls when needed
    LinkedHashSet<GitRemote> result = new LinkedHashSet<>();
    for (Remote remote : myRemotes) {
      GitRemote gitRemote = convertRemoteToGitRemote(myUrls, remote);
      if (!remote.getUrls().isEmpty()) {
        result.add(gitRemote);
      }
    }
    return result;
  }

  private static @NotNull GitRemote convertRemoteToGitRemote(@NotNull Collection<Url> urls, @NotNull Remote remote) {
    UrlsAndPushUrls substitutedUrls = substituteUrls(urls, remote);
    return new GitRemote(remote.myName, substitutedUrls.getUrls(), substitutedUrls.getPushUrls(),
                         remote.getFetchSpecs(), remote.getPushSpec());
  }

  /**
   * Create branch tracking information based on the information defined in {@code .git/config}.
   */
  @VisibleForTesting
  public @NotNull Set<GitBranchTrackInfo> parseTrackInfos(@NotNull Collection<GitLocalBranch> localBranches,
                                          @NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
    LinkedHashSet<GitBranchTrackInfo> result = new LinkedHashSet<>();
    for (BranchConfig config : myTrackedInfos) {
      ContainerUtil.addIfNotNull(result, convertBranchConfig(config, localBranches, remoteBranches));
    }
    return result;
  }

  /**
   * Return core info
   */
  @NotNull
  Core parseCore() {
    return myCore;
  }

  /**
   * Creates an instance of GitConfig by reading information from the specified {@code .git/config} file.
   * <p/>
   * If some section is invalid, it is skipped, and a warning is reported.
   */
  @VisibleForTesting
  public static @NotNull GitConfig read(@NotNull File configFile) {
    GitConfig emptyConfig = new GitConfig(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new Core(null));
    if (!configFile.exists() || configFile.isDirectory()) {
      LOG.info("No .git/config file at " + configFile.getPath());
      return emptyConfig;
    }

    Ini ini;
    try {
      ini = GitConfigHelperKt.loadIniFile(configFile);
    }
    catch (IOException e) {
      LOG.warn("Couldn't read .git/config at" + configFile.getPath(), e);
      return emptyConfig;
    }

    Pair<Collection<Remote>, Collection<Url>> remotesAndUrls = parseRemotes(ini);
    Collection<BranchConfig> trackedInfos = parseTrackedInfos(ini);
    Core core = parseCore(ini);

    return new GitConfig(remotesAndUrls.getFirst(), remotesAndUrls.getSecond(), trackedInfos, core);
  }

  private static @NotNull Collection<BranchConfig> parseTrackedInfos(@NotNull Ini ini) {
    Collection<BranchConfig> configs = new ArrayList<>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();

      BranchConfig branchConfig = parseBranchSection(sectionName, section);
      if (branchConfig != null) {
        configs.add(branchConfig);
      }
    }
    return configs;
  }

  private static @Nullable GitBranchTrackInfo convertBranchConfig(@Nullable BranchConfig branchConfig,
                                                                  @NotNull Collection<GitLocalBranch> localBranches,
                                                                  @NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
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

  private static @Nullable GitLocalBranch findLocalBranch(@NotNull String branchName, @NotNull Collection<GitLocalBranch> localBranches) {
    final String name = GitBranchUtil.stripRefsPrefix(branchName);
    return ContainerUtil.find(localBranches, input -> input.getName().equals(name));
  }

  public static @Nullable GitRemoteBranch findRemoteBranch(@NotNull String remoteBranchName,
                                                           @NotNull String remoteName,
                                                           @NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
    final String branchName = GitBranchUtil.stripRefsPrefix(remoteBranchName);
    return ContainerUtil.find(remoteBranches, branch -> branch.getNameForRemoteOperations().equals(branchName) &&
                                                        branch.getRemote().getName().equals(remoteName));
  }

  private static @Nullable BranchConfig parseBranchSection(@NotNull String sectionName,
                                                           @NotNull Profile.Section section) {
    Matcher matcher = BRANCH_INFO_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      String remote = section.get("remote");
      String merge = section.get("merge");
      String rebase = section.get("rebase");
      return new BranchConfig(matcher.group(1), remote, merge, rebase);
    }
    if (BRANCH_COMMON_PARAMS_SECTION.matcher(sectionName).matches()) {
      LOG.debug(String.format("Common branch option(s) defined .git/config. sectionName: %s%n section: %s", sectionName, section));
      return null;
    }
    return null;
  }

  private static @NotNull Pair<Collection<Remote>, Collection<Url>> parseRemotes(@NotNull Ini ini) {
    Collection<Remote> remotes = new ArrayList<>();
    Collection<Url> urls = new ArrayList<>();
    for (String sectionName : ini.keySet()) {
      Profile.Section section = ini.get(sectionName);

      Remote remote = parseRemoteSection(sectionName, section);
      if (remote != null) {
        remotes.add(remote);
      }
      else {
        Url url = parseUrlSection(sectionName, section);
        if (url != null) {
          urls.add(url);
        }
      }
    }
    return Pair.create(remotes, urls);
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
  private static @NotNull UrlsAndPushUrls substituteUrls(@NotNull Collection<Url> urlSections, @NotNull Remote remote) {
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

  private static final class UrlsAndPushUrls {
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

  private static @NotNull String substituteUrl(@NotNull String remoteUrl, @NotNull Url url, @NotNull String insteadOf) {
    return url.myName + remoteUrl.substring(insteadOf.length());
  }

  private static @Nullable Remote parseRemoteSection(@NotNull String sectionName,
                                                     @NotNull Profile.Section section) {
    Matcher matcher = REMOTE_SECTION.matcher(sectionName);
    if (matcher.matches() && matcher.groupCount() == 1) {
      List<String> fetch = ContainerUtil.notNullize(section.getAll("fetch"));
      List<String> push = ContainerUtil.notNullize(section.getAll("push"));
      List<String> url = ContainerUtil.notNullize(section.getAll("url"));
      List<String> pushurl = ContainerUtil.notNullize(section.getAll("pushurl"));
      return new Remote(matcher.group(1), fetch, push, url, pushurl);
    }
    return null;
  }

  private static @Nullable Url parseUrlSection(@NotNull String sectionName, @NotNull Profile.Section section) {
    Matcher matcher = URL_SECTION.matcher(sectionName);
    if (matcher.matches() && matcher.groupCount() == 1) {
      String insteadof = section.get("insteadof");
      String pushInsteadof = section.get("pushinsteadof");
      return new Url(matcher.group(1), insteadof, pushInsteadof);
    }
    return null;
  }

  private static @NotNull Core parseCore(@NotNull Ini ini) {
    String hooksPath = null;

    List<Profile.Section> sections = ContainerUtil.notNullize(ini.getAll(CORE_SECTION));
    for (Profile.Section section : ContainerUtil.reverse(sections)) { // take entry from last section for duplicates
      if (hooksPath == null) hooksPath = ContainerUtil.getLastItem(section.getAll("hookspath"));
    }
    return new Core(hooksPath);
  }

  private static final class Remote {

    private final @NotNull String myName;
    @NotNull List<String> myFetchSpecs;
    @NotNull List<String> myPushSpec;
    @NotNull List<String> myUrls;
    @NotNull List<String> myPushUrls;

    private Remote(@NotNull String name,
                   @NotNull List<String> fetchSpecs,
                   @NotNull List<String> pushSpec,
                   @NotNull List<String> urls,
                   @NotNull List<String> pushUrls) {
      myName = name;
      myFetchSpecs = fetchSpecs;
      myPushSpec = pushSpec;
      myUrls = urls;
      myPushUrls = pushUrls;
    }

    private @NotNull Collection<String> getUrls() {
      return myUrls;
    }

    private @NotNull Collection<String> getPushUrls() {
      return myPushUrls;
    }

    private @NotNull List<String> getPushSpec() {
      return myPushSpec;
    }

    private @NotNull List<String> getFetchSpecs() {
      return myFetchSpecs;
    }
  }

  private static final class Url {
    private final String myName;
    private final @Nullable String myInsteadof;
    private final @Nullable String myPushInsteadof;

    private Url(String name, @Nullable String insteadof, @Nullable String pushInsteadof) {
      myName = name;
      myInsteadof = insteadof;
      myPushInsteadof = pushInsteadof;
    }

    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    public @Nullable String getInsteadOf() {
      return myInsteadof;
    }

    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    public @Nullable String getPushInsteadOf() {
      return myPushInsteadof;
    }
  }

  private static final class BranchConfig {
    private final String myName;
    private final @Nullable String myRemote;
    private final @Nullable String myMerge;
    private final @Nullable String myRebase;

    private BranchConfig(String name, @Nullable String remote, @Nullable String merge, @Nullable String rebase) {
      myName = name;
      myRemote = remote;
      myMerge = merge;
      myRebase = rebase;
    }

    public String getName() {
      return myName;
    }

    private @Nullable String getRemote() {
      return myRemote;
    }

    private @Nullable String getMerge() {
      return myMerge;
    }

    private @Nullable String getRebase() {
      return myRebase;
    }
  }

  public static final class Core {
    private final @Nullable String myHooksPath;

    private Core(@Nullable String hooksPath) {
      myHooksPath = hooksPath;
    }

    public @Nullable String getHooksPath() {
      return myHooksPath;
    }
  }
}
