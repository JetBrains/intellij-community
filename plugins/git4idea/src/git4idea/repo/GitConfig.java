  // Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

  import com.intellij.openapi.diagnostic.Logger;
  import com.intellij.openapi.util.Pair;
  import com.intellij.openapi.util.text.StringUtil;
  import com.intellij.util.containers.ContainerUtil;
  import git4idea.GitLocalBranch;
  import git4idea.GitRemoteBranch;
  import git4idea.branch.GitBranchUtil;
  import one.util.streamex.StreamEx;
  import org.ini4j.Ini;
  import org.ini4j.Profile;
  import org.jetbrains.annotations.NotNull;
  import org.jetbrains.annotations.Nullable;

  import java.io.File;
  import java.io.IOException;
  import java.util.ArrayList;
  import java.util.Collection;
  import java.util.List;
  import java.util.Map;
  import java.util.regex.Matcher;
  import java.util.regex.Pattern;

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
public final class GitConfig {
  private static final Logger LOG = Logger.getInstance(GitConfig.class);

  private static final Pattern REMOTE_SECTION = Pattern.compile("(?:svn-)?remote \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern URL_SECTION = Pattern.compile("url \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern BRANCH_INFO_SECTION = Pattern.compile("branch \"(.*)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern BRANCH_COMMON_PARAMS_SECTION = Pattern.compile("branch", Pattern.CASE_INSENSITIVE);

  @NotNull private final Collection<? extends Remote> myRemotes;
  @NotNull private final Collection<? extends Url> myUrls;
  @NotNull private final Collection<? extends BranchConfig> myTrackedInfos;


  private GitConfig(@NotNull Collection<? extends Remote> remotes, @NotNull Collection<? extends Url> urls, @NotNull Collection<? extends BranchConfig> trackedInfos) {
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
    return StreamEx.of(myRemotes)
          .map(remote -> convertRemoteToGitRemote(myUrls, remote))
          .filter(remote -> !remote.getUrls().isEmpty())
          .toList();
  }

  @NotNull
  private static GitRemote convertRemoteToGitRemote(@NotNull Collection<? extends Url> urls, @NotNull Remote remote) {
    UrlsAndPushUrls substitutedUrls = substituteUrls(urls, remote);
    return new GitRemote(remote.myName, substitutedUrls.getUrls(), substitutedUrls.getPushUrls(),
                         remote.getFetchSpecs(), remote.getPushSpec());
  }

  /**
   * Create branch tracking information based on the information defined in {@code .git/config}.
   */
  @NotNull
  Collection<GitBranchTrackInfo> parseTrackInfos(@NotNull final Collection<? extends GitLocalBranch> localBranches,
                                                 @NotNull final Collection<? extends GitRemoteBranch> remoteBranches) {
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

    Ini ini;
    try {
      ini = GitConfigHelperKt.loadIniFile(configFile);
    }
    catch (IOException e) {
      return emptyConfig;
    }

    Pair<Collection<Remote>, Collection<Url>> remotesAndUrls = parseRemotes(ini);
    Collection<BranchConfig> trackedInfos = parseTrackedInfos(ini);

    return new GitConfig(remotesAndUrls.getFirst(), remotesAndUrls.getSecond(), trackedInfos);
  }

  @NotNull
  private static Collection<BranchConfig> parseTrackedInfos(@NotNull Ini ini) {
    Collection<BranchConfig> configs = new ArrayList<>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();
      if (StringUtil.startsWithIgnoreCase(sectionName, "branch")) {
        BranchConfig branchConfig = parseBranchSection(sectionName, section);
        if (branchConfig != null) {
          configs.add(branchConfig);
        }
      }
    }
    return configs;
  }

  @Nullable
  private static GitBranchTrackInfo convertBranchConfig(@Nullable BranchConfig branchConfig,
                                                        @NotNull Collection<? extends GitLocalBranch> localBranches,
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

  @Nullable
  private static GitLocalBranch findLocalBranch(@NotNull String branchName, @NotNull Collection<? extends GitLocalBranch> localBranches) {
    final String name = GitBranchUtil.stripRefsPrefix(branchName);
    return ContainerUtil.find(localBranches, input -> input.getName().equals(name));
  }

  @Nullable
  public static GitRemoteBranch findRemoteBranch(@NotNull String remoteBranchName,
                                                 @NotNull String remoteName,
                                                 @NotNull Collection<? extends GitRemoteBranch> remoteBranches) {
    final String branchName = GitBranchUtil.stripRefsPrefix(remoteBranchName);
    return ContainerUtil.find(remoteBranches, branch -> branch.getNameForRemoteOperations().equals(branchName) &&
                                                        branch.getRemote().getName().equals(remoteName));
  }

  @Nullable
  private static BranchConfig parseBranchSection(@NotNull String sectionName,
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
    LOG.error(String.format("Invalid branch section format in .git/config. sectionName: %s%n section: %s", sectionName, section));
    return null;
  }

  @NotNull
  private static Pair<Collection<Remote>, Collection<Url>> parseRemotes(@NotNull Ini ini) {
    Collection<Remote> remotes = new ArrayList<>();
    Collection<Url> urls = new ArrayList<>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();

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
  @NotNull
  private static UrlsAndPushUrls substituteUrls(@NotNull Collection<? extends Url> urlSections, @NotNull Remote remote) {
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

  @NotNull
  private static String substituteUrl(@NotNull String remoteUrl, @NotNull Url url, @NotNull String insteadOf) {
    return url.myName + remoteUrl.substring(insteadOf.length());
  }

  @Nullable
  private static Remote parseRemoteSection(@NotNull String sectionName,
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

  @Nullable
  private static Url parseUrlSection(@NotNull String sectionName, @NotNull Profile.Section section) {
    Matcher matcher = URL_SECTION.matcher(sectionName);
    if (matcher.matches() && matcher.groupCount() == 1) {
      String insteadof = section.get("insteadof");
      String pushInsteadof = section.get("pushinsteadof");
      return new Url(matcher.group(1), insteadof, pushInsteadof);
    }
    return null;
  }

  private static final class Remote {

    @NotNull private final String myName;
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

    @NotNull
    private Collection<String> getUrls() {
      return myUrls;
    }

    @NotNull
    private Collection<String> getPushUrls() {
      return myPushUrls;
    }

    @NotNull
    private List<String> getPushSpec() {
      return myPushSpec;
    }

    @NotNull
    private List<String> getFetchSpecs() {
      return myFetchSpecs;
    }
  }

  private static final class Url {
    private final String myName;
    @Nullable private final String myInsteadof;
    @Nullable private final String myPushInsteadof;

    private Url(String name, @Nullable String insteadof, @Nullable String pushInsteadof) {
      myName = name;
      myInsteadof = insteadof;
      myPushInsteadof = pushInsteadof;
    }

    @Nullable
    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    public String getInsteadOf() {
      return myInsteadof;
    }

    @Nullable
    // null means no entry, i.e. nothing to substitute. Empty string means substituting everything
    public String getPushInsteadOf() {
      return myPushInsteadof;
    }
  }

  private static final class BranchConfig {
    private final String myName;
    @Nullable private final String myRemote;
    @Nullable private final String myMerge;
    @Nullable private final String myRebase;

    private BranchConfig(String name, @Nullable String remote, @Nullable String merge, @Nullable String rebase) {
      myName = name;
      myRemote = remote;
      myMerge = merge;
      myRebase = rebase;
    }

    public String getName() {
      return myName;
    }

    @Nullable
    private String getRemote() {
      return myRemote;
    }

    @Nullable
    private String getMerge() {
      return myMerge;
    }

    @Nullable
    private String getRebase() {
      return myRebase;
    }
  }
}
