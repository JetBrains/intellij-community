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
package git4idea.jgit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
final class GitHttpProxySupport {

  private GitHttpProxySupport() {
  }

  public static void init() throws IOException {
    HttpConfigurable.getInstance().setAuthenticator();
  }

  @NotNull
  static ProxySelector newProxySelector(@Nullable ProxySelector defaultProxySelector, @NotNull String url) {
    return new IdeaProxySelector(defaultProxySelector, url);
  }

  static boolean shouldUseProxy() {
    HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    return proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST);
  }
  
  private static class IdeaProxySelector extends ProxySelector {
    
    private static final Logger LOG = Logger.getInstance(IdeaProxySelector.class);

    @NotNull private final HttpConfigurable myConfigurable;
    @Nullable private final ProxySelector myDefaultProxySelector;
    @NotNull private final String myUrl;

    IdeaProxySelector(@Nullable ProxySelector defaultProxySelector, @NotNull String url) {
      myDefaultProxySelector = defaultProxySelector;
      myUrl = url;
      myConfigurable = HttpConfigurable.getInstance();
    }

    @Override
    public List<Proxy> select(URI uri) {
      if (urlMatches(uri)) {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(myConfigurable.PROXY_HOST, myConfigurable.PROXY_PORT));
        List<Proxy> proxies = new ArrayList<Proxy>();
        proxies.add(proxy);
        if (myDefaultProxySelector != null) {
          proxies.addAll(myDefaultProxySelector.select(uri));
        }
        return proxies;
      }
      else if (myDefaultProxySelector != null) {
        return myDefaultProxySelector.select(uri);
      }
      else {
        return Collections.singletonList(Proxy.NO_PROXY);
      }
    }

    private boolean urlMatches(URI uri) {
      try {
        // comparing with the host, because the HttpClient requests not only the given git-http url,
        // but also opens a socket connected with the server through the URL like "socket://github.com:443".
        return uri.getHost().endsWith(new URL(myUrl).getHost());
      }
      catch (MalformedURLException e) {
        LOG.info("Couldn't create URL object from " + myUrl, e);
        return false;
      }
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    }
  }
  
}
