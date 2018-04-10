/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import com.intellij.util.net.IdeHttpClientHelpers;
import com.intellij.util.net.ssl.CertificateManager;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSettings;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class GithubConnectionBuilder {
  @NotNull private final GithubAuthData myAuth;
  @NotNull private final String myApiURL;

  public GithubConnectionBuilder(@NotNull GithubAuthData auth, @NotNull String apiURL) {
    myAuth = auth;
    myApiURL = apiURL;
  }

  @NotNull
  public CloseableHttpClient createClient() {
    HttpClientBuilder builder = HttpClients.custom();

    builder
      .setDefaultRequestConfig(createRequestConfig())
      .setDefaultConnectionConfig(createConnectionConfig())
      .setDefaultHeaders(createHeaders())
      .setSslcontext(CertificateManager.getInstance().getSslContext());

    setupCredentialsProvider(builder);

    return builder.build();
  }

  @NotNull
  private RequestConfig createRequestConfig() {
    RequestConfig.Builder builder = RequestConfig.custom();

    int timeout = GithubSettings.getInstance().getConnectionTimeout();
    builder
      .setConnectTimeout(timeout)
      .setSocketTimeout(timeout);

    if (myAuth.isUseProxy()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyForUrlIfEnabled(builder, myApiURL);
    }

    return builder.build();
  }

  @NotNull
  private ConnectionConfig createConnectionConfig() {
    return ConnectionConfig.custom()
      .setCharset(Consts.UTF_8)
      .build();
  }


  @NotNull
  private CredentialsProvider setupCredentialsProvider(@NotNull HttpClientBuilder builder) {
    CredentialsProvider provider = new BasicCredentialsProvider();
    // Basic authentication
    GithubAuthData.BasicAuth basicAuth = myAuth.getBasicAuth();
    if (basicAuth != null) {
      AuthScope authScope = getBasicAuthScope();

      provider.setCredentials(authScope, new UsernamePasswordCredentials(basicAuth.getLogin(), basicAuth.getPassword()));
      builder.addInterceptorFirst(new PreemptiveBasicAuthInterceptor(authScope));
    }
    builder.setDefaultCredentialsProvider(provider);

    if (myAuth.isUseProxy()) {
      IdeHttpClientHelpers.ApacheHttpClient4.setProxyCredentialsForUrlIfEnabled(provider, myApiURL);
    }

    return provider;
  }

  @NotNull
  private AuthScope getBasicAuthScope() {
    try {
      URIBuilder builder = new URIBuilder(myApiURL);
      return new AuthScope(builder.getHost(), builder.getPort(), AuthScope.ANY_REALM, AuthSchemes.BASIC);
    }
    catch (URISyntaxException e) {
      return AuthScope.ANY;
    }
  }

  @NotNull
  private Collection<? extends Header> createHeaders() {
    List<Header> headers = new ArrayList<>();
    GithubAuthData.TokenAuth tokenAuth = myAuth.getTokenAuth();
    if (tokenAuth != null) {
      headers.add(new BasicHeader("Authorization", "token " + tokenAuth.getToken()));
    }
    GithubAuthData.BasicAuth basicAuth = myAuth.getBasicAuth();
    if (basicAuth != null && basicAuth.getCode() != null) {
      headers.add(new BasicHeader("X-GitHub-OTP", basicAuth.getCode()));
    }
    return headers;
  }

  private static class PreemptiveBasicAuthInterceptor implements HttpRequestInterceptor {
    @NotNull private final AuthScope myBasicAuthScope;

    public PreemptiveBasicAuthInterceptor(@NotNull AuthScope basicAuthScope) {
      myBasicAuthScope = basicAuthScope;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException {
      CredentialsProvider provider = (CredentialsProvider)context.getAttribute(HttpClientContext.CREDS_PROVIDER);
      Credentials credentials = provider.getCredentials(myBasicAuthScope);
      if (credentials != null) {
        request.addHeader(new BasicScheme(Consts.UTF_8).authenticate(credentials, request, context));
      }
    }
  }
}
