// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

@Tag("remote-server")
public final class RemoteServerState {
  @Attribute("name")
  public String myName;
  @Attribute("type")
  public String myTypeId;
  @Tag("configuration")
  public Element myConfiguration;
}
