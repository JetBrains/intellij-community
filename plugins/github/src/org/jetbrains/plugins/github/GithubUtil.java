package org.jetbrains.plugins.github;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Ref;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author oleg
 */
public class GithubUtil {
  public static final String GITHUB_HOST = "https://github.com";
  private static final String API_URL = "/api/v2/xml";
  private static final Logger LOG = Logger.getInstance(GithubUtil.class.getName());

  public static <T> T accessToGithubWithModalProgress(final Project project, final Computable<T> computable) throws CancelledException {
    final Ref<T> result = new Ref<T>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to github", true) {
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

  public static boolean testConnection(final String login, final String password) {
    try {
      final HttpMethod method = doREST(login, password, "/user/show/" + login, false);
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
    return false;
  }

  public static HttpMethod doREST(final String login, final String password, final String request, final boolean post) throws Exception {
    final HttpClient client = getHttpClient(login, password);
    client.getParams().setContentCharset("UTF-8");
    final String uri = JDOMUtil.escapeText(getUrl() + request, true, true);
    final HttpMethod method = post ? new PostMethod(uri) : new GetMethod(uri);
    client.executeMethod(method);
    return method;
  }

  public static HttpClient getHttpClient(final String login, final String password) {
    final HttpClient client = new HttpClient();
    client.getParams().setAuthenticationPreemptive(true);
    client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(login, password));
    return client;
  }

  private static String getUrl() {
    return GITHUB_HOST + API_URL;
  }

  public static List<RepositoryInfo> getAvailableRepos(final String login, final String password, final boolean ownOnly) {
    try {
      final String request = (ownOnly ? "/repos/show/" : "/repos/watched/") + login;
      final HttpMethod method = doREST(login, password, request, false);
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
        result.add(new RepositoryInfo(repo.getChildText("name"), repo.getChildText("owner")));
      }
      return result;
    }
    catch (Exception e) {
      // ignore
    }
    return Collections.emptyList();
  }

  public static boolean isPrivateRepoAllowed(final String login, final String password) {
    try {
      final String request = "/user/show/" + login;
      final HttpMethod method = doREST(login, password, request, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      if ("error".equals(element.getName())){
        LOG.warn("Got error element by request: " + request);
        return false;
      }
      final Element plan = element.getChild("plan");
      assert plan != null : "Authentification failed";
      final String privateRepos = plan.getChildText("private_repos");
      return privateRepos != null && Integer.valueOf(privateRepos) > 0;
    }
    catch (Exception e) {
      // ignore
    }
    return false;
  }

  public static class CancelledException extends RuntimeException {}

  /**
   * Shows GitHub login settings if credentials are wrong or empty and return the list of all the watched repos by user
   * @param project
   * @return
   */
  @Nullable
  public static List<RepositoryInfo> getAvailableRepos(final Project project, final boolean ownOnly) {
    final GithubSettings settings = GithubSettings.getInstance();
    final boolean validCredentials;
    try {
      validCredentials = accessToGithubWithModalProgress(project, new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Trying to login to GitHub");
          return testConnection(settings.getLogin(), settings.getPassword());
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
      return accessToGithubWithModalProgress(project, new Computable<List<RepositoryInfo>>() {
        @Override
        public List<RepositoryInfo> compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Extracting info about available repositories");
          return getAvailableRepos(settings.getLogin(), settings.getPassword(), ownOnly);
        }
      });
    }
    catch (CancelledException e) {
      return null;
    }
  }

}
