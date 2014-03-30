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

  import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitRemoteBranch;
import git4idea.GitSvnRemoteBranch;
import git4idea.branch.GitBranchUtil;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Reads information from the {@code .git/config} file, and parses it to actual objects.</p>
 *
 * <p>Currently doesn't read all the information: general information about remotes and branch tracking</p>
 *
 * <p>Parsing is performed with the help of <a href="http://ini4j.sourceforge.net/">ini4j</a> library.</p>
 *
 * TODO: note, that other git configuration files (such as ~/.gitconfig) are not handled yet.
 * 
 * @author Kirill Likhodedov
 */
public class GitConfig {

  /**
   * Special remote typical for git-svn configuration:
   * <pre>[branch "trunk]
   *   remote = .
   *   merge = refs/remotes/trunk
   * </pre>
   * @deprecated Use {@link GitSvnRemoteBranch}
   */
  @Deprecated
  public static final String DOT_REMOTE = ".";

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
    return ContainerUtil.map(myRemotes, new Function<Remote, GitRemote>() {
      @Override
      public GitRemote fun(@Nullable Remote remote) {
        assert remote != null;
        return convertRemoteToGitRemote(myUrls, remote);
      }
    });
  }

  @NotNull
  private static GitRemote convertRemoteToGitRemote(@NotNull Collection<Url> urls, @NotNull Remote remote) {
    UrlsAndPushUrls substitutedUrls = substituteUrls(urls, remote);
    return new GitRemote(remote.myName, substitutedUrls.getUrls(), substitutedUrls.getPushUrls(),
                         remote.getFetchSpecs(), computePushSpec(remote));
  }

  /**
   * Create branch tracking information based on the information defined in {@code .git/config}.
   */
  @NotNull
  Collection<GitBranchTrackInfo> parseTrackInfos(@NotNull final Collection<GitLocalBranch> localBranches,
                                                 @NotNull final Collection<GitRemoteBranch> remoteBranches) {
    return ContainerUtil.mapNotNull(myTrackedInfos, new Function<BranchConfig, GitBranchTrackInfo>() {
      @Override
      public GitBranchTrackInfo fun(BranchConfig config) {
        if (config != null) {
          return convertBranchConfig(config, localBranches, remoteBranches);
        }
        return null;
      }
    });
  }

  /**
   * Creates an instance of GitConfig by reading information from the specified {@code .git/config} file.
   * @throws RepoStateException if {@code .git/config} couldn't be read or has invalid format.<br/>
   *         If in general it has valid format, but some sections are invalid, it skips invalid sections, but reports an error.
   */
  @NotNull
  static GitConfig read(@NotNull GitPlatformFacade platformFacade, @NotNull File configFile) {
    Ini ini = new Ini();
    ini.getConfig().setMultiOption(true);  // duplicate keys (e.g. url in [remote])
    ini.getConfig().setTree(false);        // don't need tree structure: it corrupts url in section name (e.g. [url "http://github.com/"]
    try {
      ini.load(configFile);
    }
    catch (IOException e) {
      LOG.error(new RepoStateException("Couldn't load .git/config file at " + configFile.getPath(), e));
      return new GitConfig(Collections.<Remote>emptyList(), Collections.<Url>emptyList(), Collections.<BranchConfig>emptyList());
    }

    IdeaPluginDescriptor plugin = platformFacade.getPluginByClassName(GitConfig.class.getName());
    ClassLoader classLoader = plugin == null ? null : plugin.getPluginClassLoader(); // null if IDEA is started from IDEA

    Pair<Collection<Remote>, Collection<Url>> remotesAndUrls = parseRemotes(ini, classLoader);
    Collection<BranchConfig> trackedInfos = parseTrackedInfos(ini, classLoader);
    
    return new GitConfig(remotesAndUrls.getFirst(), remotesAndUrls.getSecond(), trackedInfos);
  }

  @NotNull
  private static Collection<BranchConfig> parseTrackedInfos(@NotNull Ini ini, @Nullable ClassLoader classLoader) {
    Collection<BranchConfig> configs = new ArrayList<BranchConfig>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();
      if (sectionName.startsWith("branch")) {
        BranchConfig branchConfig = parseBranchSection(sectionName, section,  classLoader);
        if (branchConfig != null) {
          configs.add(branchConfig);
        }
      }
    }
    return configs;
  }

  @Nullable
  private static GitBranchTrackInfo convertBranchConfig(@Nullable BranchConfig branchConfig,
                                                        @NotNull Collection<GitLocalBranch> localBranches,
                                                        @NotNull Collection<GitRemoteBranch> remoteBranches) {
    if (branchConfig == null) {
      return null;
    }
    final String branchName = branchConfig.getName();
    String remoteName = branchConfig.getBean().getRemote();
    String mergeName = branchConfig.getBean().getMerge();
    String rebaseName = branchConfig.getBean().getRebase();

    if (StringUtil.isEmptyOrSpaces(mergeName) && StringUtil.isEmptyOrSpaces(rebaseName)) {
      LOG.info("No branch." + branchName + ".merge/rebase item in the .git/config");
      return null;
    }
    if (StringUtil.isEmptyOrSpaces(remoteName)) {
      LOG.info("No branch." + branchName + ".remote item in the .git/config");
      return null;
    }

    boolean merge = mergeName != null;
    final String remoteBranchName = (merge ? mergeName : rebaseName);
    assert remoteName != null;
    assert remoteBranchName != null;

    GitLocalBranch localBranch = findLocalBranch(branchName, localBranches);
    GitRemoteBranch remoteBranch = GitBranchUtil.findRemoteBranchByName(remoteBranchName, remoteName, remoteBranches);
    if (localBranch == null || remoteBranch == null) {
      return null;
    }
    return new GitBranchTrackInfo(localBranch, remoteBranch, merge);
  }

  @Nullable
  private static GitLocalBranch findLocalBranch(@NotNull String branchName, @NotNull Collection<GitLocalBranch> localBranches) {
    final String name = GitBranchUtil.stripRefsPrefix(branchName);
    try {
      return ContainerUtil.find(localBranches, new Condition<GitLocalBranch>() {
        @Override
        public boolean value(@Nullable GitLocalBranch input) {
          assert input != null;
          return input.getName().equals(name);
        }
      });
    }
    catch (NoSuchElementException e) {
      LOG.info("Couldn't find branch with name " + name);
      return null;
    }
  }

  @Nullable
  private static BranchConfig parseBranchSection(String sectionName, Profile.Section section, @Nullable ClassLoader classLoader) {
    BranchBean branchBean = section.as(BranchBean.class, classLoader);
    Matcher matcher = BRANCH_INFO_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      return new BranchConfig(matcher.group(1), branchBean);
    }
    if (BRANCH_COMMON_PARAMS_SECTION.matcher(sectionName).matches()) {
      LOG.debug(String.format("Common branch option(s) defined .git/config. sectionName: %s%n section: %s", sectionName, section));
      return null;
    }
    LOG.error(String.format("Invalid branch section format in .git/config. sectionName: %s%n section: %s", sectionName, section));
    return null;
  }

  @NotNull
  private static Pair<Collection<Remote>, Collection<Url>> parseRemotes(@NotNull Ini ini, @Nullable ClassLoader classLoader) {
    Collection<Remote> remotes = new ArrayList<Remote>();
    Collection<Url> urls = new ArrayList<Url>();
    for (Map.Entry<String, Profile.Section> stringSectionEntry : ini.entrySet()) {
      String sectionName = stringSectionEntry.getKey();
      Profile.Section section = stringSectionEntry.getValue();

      if (sectionName.startsWith("remote") || sectionName.startsWith("svn-remote")) {
        Remote remote = parseRemoteSection(sectionName, section, classLoader);
        if (remote != null) {
          remotes.add(remote);
        }
      }
      else if (sectionName.startsWith("url")) {
        Url url = parseUrlSection(sectionName, section, classLoader);
        if (url != null) {
          urls.add(url);
        }
      }
    }
    return Pair.create(remotes, urls);
  }

  @NotNull
  private static List<String> computePushSpec(@NotNull Remote remote) {
    List<String> pushSpec = remote.getPushSpec();
    return pushSpec == null ? remote.getFetchSpecs() : pushSpec;
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
    List<String> urls = new ArrayList<String>(remote.getUrls().size());
    Collection<String> pushUrls = new ArrayList<String>();

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
      pushUrls = new ArrayList<String>(urls);
    }

    return new UrlsAndPushUrls(urls, pushUrls);
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
  private static String substituteUrl(@NotNull String remoteUrl, @NotNull Url url, @NotNull String insteadOf) {
    return url.myName + remoteUrl.substring(insteadOf.length());
  }

  @Nullable
  private static Remote parseRemoteSection(@NotNull String sectionName, @NotNull Profile.Section section, @Nullable ClassLoader classLoader) {
    RemoteBean remoteBean = section.as(RemoteBean.class, classLoader);
    Matcher matcher = REMOTE_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      return new Remote(matcher.group(1), remoteBean);
    }
    LOG.error(String.format("Invalid remote section format in .git/config. sectionName: %s section: %s", sectionName, section));
    return null;
  }

  @Nullable
  private static Url parseUrlSection(@NotNull String sectionName, @NotNull Profile.Section section, @Nullable ClassLoader classLoader) {
    UrlBean urlBean = section.as(UrlBean.class, classLoader);
    Matcher matcher = URL_SECTION.matcher(sectionName);
    if (matcher.matches()) {
      return new Url(matcher.group(1), urlBean);
    }
    LOG.error(String.format("Invalid url section format in .git/config. sectionName: %s section: %s", sectionName, section));
    return null;
  }

  private static class Remote {

    private final String myName;
    private final RemoteBean myRemoteBean;

    private Remote(@NotNull String name, @NotNull RemoteBean remoteBean) {
      myRemoteBean = remoteBean;
      myName = name;
    }
    
    @NotNull
    private Collection<String> getUrls() {
      return nonNullCollection(myRemoteBean.getUrl());
    }

    @NotNull
    private Collection<String> getPushUrls() {
      return nonNullCollection(myRemoteBean.getPushUrl());
    }

    @Nullable
    // no need in wrapping null here - we check for it in #computePushSpec 
    private List<String> getPushSpec() {
      String[] push = myRemoteBean.getPush();
      return push == null ? null : Arrays.asList(push);
    }

    @NotNull
    private List<String> getFetchSpecs() {
      return Arrays.asList(notNull(myRemoteBean.getFetch()));
    }
    
  }

  private interface RemoteBean {
    @Nullable String[] getFetch();
    @Nullable String[] getPush();
    @Nullable String[] getUrl();
    @Nullable String[] getPushUrl();
  }

  private static class Url {
    private final String myName;
    private final UrlBean myUrlBean;

    private Url(String name, UrlBean urlBean) {
      myUrlBean = urlBean;
      myName = name;
    }

    @Nullable
    // null means to entry, i.e. nothing to substitute. Empty string means substituting everything
    public String getInsteadOf() {
      return myUrlBean.getInsteadOf();
    }

    @Nullable
    // null means to entry, i.e. nothing to substitute. Empty string means substituting everything
    public String getPushInsteadOf() {
      return myUrlBean.getPushInsteadOf();
    }
  }

  private interface UrlBean {
    @Nullable String getInsteadOf();
    @Nullable String getPushInsteadOf();
  }
  
  private static class BranchConfig {
    private final String myName;
    private final BranchBean myBean;

    public BranchConfig(String name, BranchBean bean) {
      myName = name;
      myBean = bean;
    }

    public String getName() {
      return myName;
    }

    public BranchBean getBean() {
      return myBean;
    }
  }
  
  private interface BranchBean {
    @Nullable String getRemote();
    @Nullable String getMerge();
    @Nullable String getRebase();
  }

  @NotNull
  private static String[] notNull(@Nullable String[] s) {
    return s == null ? ArrayUtil.EMPTY_STRING_ARRAY : s;
  }

  @NotNull
  private static Collection<String> nonNullCollection(@Nullable String[] array) {
    return array == null ? Collections.<String>emptyList() : new ArrayList<String>(Arrays.asList(array));
  }

}
