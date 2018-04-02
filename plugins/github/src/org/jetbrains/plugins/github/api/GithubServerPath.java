// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Github server reference allowing to specify custom port and path to instance
 */
@Tag("server")
public class GithubServerPath {
  @Attribute("host")
  @NotNull private final String myHost;
  @Attribute("port")
  @Nullable private final Integer myPort;
  @Attribute("suffix")
  @Nullable private final String mySuffix;

  public GithubServerPath() {
    this("", null, null);
  }

  public GithubServerPath(@NonNls @NotNull String host) {
    this(host, null, null);
  }

  public GithubServerPath(@NonNls @NotNull String host, @Nullable Integer port, @NonNls @Nullable String suffix) {
    myHost = host.toLowerCase(Locale.ENGLISH);
    myPort = port;
    mySuffix = suffix != null ? suffix.toLowerCase(Locale.ENGLISH) : null;
  }

  @NotNull
  public String getHost() {
    return myHost;
  }

  @Nullable
  public Integer getPort() {
    return myPort;
  }

  @Nullable
  public String getSuffix() {
    return mySuffix;
  }

  public String toString() {
    String port = myPort != null ? (":" + myPort.toString()) : "";
    return myHost + port + StringUtil.notNullize(mySuffix);
  }
}
