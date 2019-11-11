// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

public class CucumberJvmTestSourceReadEvent {
  private final String myUri;

  private final String mySource;

  public CucumberJvmTestSourceReadEvent(String uri, String source) {
    myUri = uri;
    mySource = source;
  }

  public String getUri() {
    return myUri;
  }

  public String getSource() {
    return mySource;
  }
}
