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
package org.jetbrains.plugins.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import javax.swing.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 */
public class GithubUtil {
  private static final String API_URL = "/api/v2/xml";
  private static final Logger LOG = Logger.getInstance(GithubUtil.class.getName());
  public static final Icon GITHUB_ICON = IconLoader.getIcon("/org/jetbrains/plugins/github/github_icon.png");


  public static String getHttpsUrl() {
    return "https://" + GithubSettings.getInstance().getHost();
  }

  public static String getHostByUrl(final String url) {
    return url.startsWith("https://") ? url.substring(8) : url.startsWith("http://") ? url.substring(7) : url.startsWith("git@") ? url.substring(4) : url;
  }

  public static <T> T accessToGithubWithModalProgress(final Project project, final Computable<T> computable) throws CancelledException {
    final Ref<T> result = new Ref<T>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        result.set(computable.compute());
      }

      @Override
      public void onCancel() {
        throw new CancelledException();
      }
    });
    return result.get();
  }

  public static void accessToGithubWithModalProgress(final Project project, final Runnable runnable) throws CancelledException {
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        runnable.run();
      }

      @Override
      public void onCancel() {
        throw new CancelledException();
      }
    });
  }

  public static boolean testConnection(final String url, final String login, final String password) {
    HttpMethod method = null;
    try {
      method = doREST(url, login, password, "/user/show/" + login, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      if ("error".equals(element.getName())){
        return false;
      }
      // In case if authentification was successful we should see some extra fields
      return element.getChild("total-private-repo-count") != null;
    }
    catch (Exception e) {
      // Ignore
    }
    finally {
      if (method!=null) {
        method.releaseConnection();
      }
    }
    return false;
  }

  public static HttpMethod doREST(final String url, final String login, final String password, final String request, final boolean post) throws Exception {
    final HttpClient client = getHttpClient(login, password);
    final String uri = "https://" + getHostByUrl(url) + API_URL + request;
    final HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    client.executeMethod(method);
    return method;
  }

  public static HttpClient getHttpClient(@Nullable final String login, @Nullable final String password) {
    final HttpClient client = new HttpClient();
    client.getParams().setContentCharset("UTF-8");
    // Configure proxySettings if it is required
    final HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    if (proxySettings.USE_HTTP_PROXY){
      client.getHostConfiguration().setProxy(proxySettings.PROXY_HOST, proxySettings.PROXY_PORT);
      if (proxySettings.PROXY_AUTHENTICATION) {
        client.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxySettings.PROXY_LOGIN,
                                                                                             proxySettings.getPlainProxyPassword()));
      }
    }
    if (login != null && password != null) {
      client.getParams().setCredentialCharset("UTF-8");
      client.getParams().setAuthenticationPreemptive(true);
      client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(login, password));
    }
    return client;
  }

  public static List<RepositoryInfo> getAvailableRepos(final String url, final String login, final String password, final boolean ownOnly) {
    HttpMethod method = null;
    try {
      final String request = (ownOnly ? "/repos/show/" : "/repos/watched/") + login;
      method = doREST(url, login, password, request, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      if ("error".equals(element.getName())){
        LOG.warn("Got error element by request: " + request);
        return Collections.emptyList();
      }
      final List repositories = element.getChildren();
      final List<RepositoryInfo> result = new ArrayList<RepositoryInfo>();
      for (int i = 0; i < repositories.size(); i++) {
        final Element repo = (Element)repositories.get(i);
        result.add(new RepositoryInfo(repo));
      }
      return result;
    }
    catch (Exception e) {
      // ignore
    }
    finally {
      if (method != null){
        method.releaseConnection();
      }
    }
    return Collections.emptyList();
  }


  @Nullable
  public static RepositoryInfo getDetailedRepoInfo(final String url, final String login, final String password, final String owner, final String name) {
    HttpMethod method = null;
    try {
      final String request = "/repos/show/" + owner + "/" + name;
      method = doREST(url, login, password, request, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      if ("error".equals(element.getName())){
        LOG.warn("Got error element by request: " + request);
        return null;
      }
      return (new RepositoryInfo(element));
    }
    catch (Exception e) {
      // ignore
    }
    finally {
      if (method != null){
        method.releaseConnection();
      }
    }
    return null;
  }

  public static boolean isPrivateRepoAllowed(final String url, final String login, final String password) {
    HttpMethod method = null;
    try {
      final String request = "/user/show/" + login;
      method = doREST(url, login, password, request, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      if ("error".equals(element.getName())){
        LOG.warn("Got error element by request: " + request);
        return false;
      }
      final Element plan = element.getChild("plan");
      assert plan != null : "Authentification failed";
      final String privateRepos = plan.getChildText("private-repos");
      return privateRepos != null && Integer.valueOf(privateRepos) > 0;
    }
    catch (Exception e) {
      // ignore
    }
    finally {
      if (method != null){
        method.releaseConnection();
      }
    }
    return false;
  }

  public static boolean checkCredentials(final Project project) {
    final GithubSettings settings = GithubSettings.getInstance();
    return checkCredentials(project, settings.getHost(), settings.getLogin(), settings.getPassword());
  }

  public static boolean checkCredentials(final Project project, final String url, final String login, final String password) {
    if (StringUtil.isEmptyOrSpaces(url) || StringUtil.isEmptyOrSpaces(login) || StringUtil.isEmptyOrSpaces(password)){
      return false;
    }
    return accessToGithubWithModalProgress(project, new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
        return testConnection(url, login, password);
      }
    });
  }

  public static class CancelledException extends RuntimeException {}

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static List<RepositoryInfo> getAvailableRepos(final Project project, final boolean ownOnly) {
    while (!checkCredentials(project)){
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()){
        return null;
      }
    }
    // Otherwise our credentials are valid and they are successfully stored in settings
    try {
      final GithubSettings settings = GithubSettings.getInstance();
      final String validPassword = settings.getPassword();
      return accessToGithubWithModalProgress(project, new Computable<List<RepositoryInfo>>() {
        @Override
        public List<RepositoryInfo> compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
          return getAvailableRepos(settings.getHost(), settings.getLogin(), validPassword, ownOnly);
        }
      });
    }
    catch (CancelledException e) {
      return null;
    }
  }

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static RepositoryInfo getDetailedRepositoryInfo(final Project project, final String owner, final String name) {
    final GithubSettings settings = GithubSettings.getInstance();
    final String password = settings.getPassword();
    final boolean validCredentials;
    try {
      validCredentials = accessToGithubWithModalProgress(project, new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
          return testConnection(settings.getHost(), settings.getLogin(), password);
        }
      });
    }
    catch (CancelledException e) {
      return null;
    }
    if (!validCredentials){
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()) {
        return null;
      }
    }
    // Otherwise our credentials are valid and they are successfully stored in settings
    try {
      final String validPassword = settings.getPassword();
      return accessToGithubWithModalProgress(project, new Computable<RepositoryInfo>() {
        @Override
        public RepositoryInfo compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Extracting detailed info about repository ''" + name + "''");
          return getDetailedRepoInfo(settings.getHost(), settings.getLogin(), validPassword, owner, name);
        }
      });
    }
    catch (CancelledException e) {
      return null;
    }
  }

  @Nullable
  public static GitRemote findGitHubRemoteBranch(final GitRepository repository) {
    // i.e. find origin which points on my github repo
    // Check that given repository is properly configured git repository

    for (GitRemote gitRemote : repository.getRemotes()) {
      if (getGithubUrl(gitRemote) != null){
        return gitRemote;
      }
    }
    return null;
  }

  @Nullable
  public static String getGithubUrl(final GitRemote gitRemote){
    final GithubSettings githubSettings = GithubSettings.getInstance();
    final String host = githubSettings.getHost();
    final String username = githubSettings.getLogin();

    final String userRepoMarkerSSHProtocol = host + ":" + username + "/";
    final String userRepoMarkerOtherProtocols = host + "/" + username + "/";
    for (String pushUrl : gitRemote.getUrls()) {
      if (pushUrl.contains(userRepoMarkerSSHProtocol) || pushUrl.contains(userRepoMarkerOtherProtocols)) {
        return pushUrl;
      }
    }
    return null;
  }
  
  public static boolean testGitExecutable(final Project project) {
    final GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
    final String executable = settings.getPathToGit();
    final GitVersion version;
    try {
      version = GitVersion.identifyVersion(executable);
    } catch (Exception e) {
      Messages.showErrorDialog(project, e.getMessage(), GitBundle.getString("find.git.error.title"));
      return false;
    }

    if (!version.isSupported()) {
      Messages.showWarningDialog(project, GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                                 GitBundle.getString("find.git.success.title"));
      return false;
    }
    return true;
  }

}
