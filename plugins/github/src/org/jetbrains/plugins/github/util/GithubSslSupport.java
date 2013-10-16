/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CalledInAwt;
import com.intellij.util.ThrowableConvertor;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.contrib.ssl.EasySSLProtocolSocketFactory;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.security.validator.ValidatorException;

import java.io.IOException;

/**
 * Provides various methods to work with SSL certificate protected HTTPS connections.
 *
 * @author Kirill Likhodedov
 */
public class GithubSslSupport {

  public static GithubSslSupport getInstance() {
    return ServiceManager.getService(GithubSslSupport.class);
  }

  /**
   * Tries to execute the {@link HttpMethod} and captures the {@link ValidatorException exception} which is thrown if user connects
   * to an HTTPS server with a non-trusted (probably, self-signed) SSL certificate. In which case proposes to cancel the connection
   * or to proceed without certificate check.
   *
   * @param methodCreator a function to create the HttpMethod. This is required instead of just {@link HttpMethod} instance, because the
   *                      implementation requires the HttpMethod to be recreated in certain circumstances.
   * @return the HttpMethod instance which was actually executed
   * and which can be {@link HttpMethod#getResponseBodyAsString() asked for the response}.
   * @throws IOException in case of other errors or if user declines the proposal of non-trusted connection.
   */
  @NotNull
  public HttpMethod executeSelfSignedCertificateAwareRequest(@NotNull HttpClient client,
                                                             @NotNull String uri,
                                                             @NotNull ThrowableConvertor<String, HttpMethod, IOException> methodCreator)
    throws IOException {
    HttpMethod method = methodCreator.convert(uri);
    try {
      client.executeMethod(method);
      return method;
    }
    catch (IOException e) {
      HttpMethod m = handleCertificateExceptionAndRetry(e, method.getURI().getHost(), client, method.getURI(), methodCreator);
      if (m == null) {
        throw e;
      }
      return m;
    }
  }

  @Nullable
  private static HttpMethod handleCertificateExceptionAndRetry(@NotNull IOException e,
                                                               @NotNull String host,
                                                               @NotNull HttpClient client,
                                                               @NotNull URI uri,
                                                               @NotNull ThrowableConvertor<String, HttpMethod, IOException> methodCreator)
    throws IOException {
    if (!isCertificateException(e)) {
      throw e;
    }

    if (isTrusted(host)) {
      // creating a special configuration that allows connections to non-trusted HTTPS hosts
      // see the javadoc to EasySSLProtocolSocketFactory for details
      Protocol easyHttps = new Protocol("https", (ProtocolSocketFactory)new EasySSLProtocolSocketFactory(), 443);
      HostConfiguration hc = new HostConfiguration();
      hc.setHost(host, 443, easyHttps);
      String relativeUri = new URI(uri.getPathQuery(), false).getURI();
      // it is important to use relative URI here, otherwise our custom protocol won't work.
      // we have to recreate the method, because HttpMethod#setUri won't overwrite the host,
      // and changing host by hands (HttpMethodBase#setHostConfiguration) is deprecated.
      HttpMethod method = methodCreator.convert(relativeUri);
      client.executeMethod(hc, method);
      return method;
    }
    throw e;
  }

  public static boolean isCertificateException(IOException e) {
    return e.getCause() instanceof ValidatorException;
  }

  private static boolean isTrusted(@NotNull String host) {
    return GithubSettings.getInstance().getTrustedHosts().contains(host.toLowerCase());
  }

  private static void saveToTrusted(@NotNull String host) {
    GithubSettings.getInstance().addTrustedHost(host.toLowerCase());
  }

  @CalledInAwt
  public boolean askIfShouldProceed(final String url) {
    String host = GithubUrlUtil.getHostFromUrl(url);

    int choice = Messages.showYesNoDialog("The security certificate of " + host + " is not trusted. Do you want to proceed anyway?",
                                          "Not Trusted Certificate", "Proceed anyway", "No, I don't trust", Messages.getErrorIcon());
    boolean trust = (choice == Messages.YES);
    if (trust) {
      saveToTrusted(host);
    }
    return trust;
  }

}
