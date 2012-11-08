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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
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
  static ProxySelector newProxySelector() {
    return new IdeaProxySelector();
  }

  static boolean shouldUseProxy() {
    HttpConfigurable proxySettings = HttpConfigurable.getInstance();
    return proxySettings.USE_HTTP_PROXY && !StringUtil.isEmptyOrSpaces(proxySettings.PROXY_HOST);
  }
  
  private static class IdeaProxySelector extends ProxySelector {
    
    private final HttpConfigurable myConfigurable;
    
    IdeaProxySelector() {
      myConfigurable = HttpConfigurable.getInstance();
    }

    @Override
    public List<Proxy> select(URI uri) {
      Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(myConfigurable.PROXY_HOST, myConfigurable.PROXY_PORT));
      return Collections.singletonList(proxy);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    }
  }
  
}
