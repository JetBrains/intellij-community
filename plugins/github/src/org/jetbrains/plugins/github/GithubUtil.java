package org.jetbrains.plugins.github;

import com.intellij.openapi.util.JDOMUtil;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

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
    final String uri = getUrl() + JDOMUtil.escapeText(request).replaceAll(" ", "%20");
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

  public static List<RepositoryInfo> getAvailableRepos(final String login, final String password) {
    try {
      final HttpMethod method = doREST(login, password, "/repos/show/" + login, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
      final List repositories = element.getChildren();
      final List<RepositoryInfo> result = new ArrayList<RepositoryInfo>();
      for (int i = 0; i < repositories.size(); i++) {
        final Element repo = (Element)repositories.get(i);
        result.add(new RepositoryInfo(repo.getChildText("name"), repo.getChildText("description"), repo.getChildText("url")));
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
      final HttpMethod method = doREST(login, password, "/user/show/" + login, false);
      final InputStream stream = method.getResponseBodyAsStream();
      final Element element = new SAXBuilder(false).build(stream).getRootElement();
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
}
