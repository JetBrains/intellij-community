/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.remoteServer.impl.configuration;

import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class RemoteServersManagerState {
  @Property(surroundWithTag = false)
  @XCollection
  public List<RemoteServerState> myServers = new ArrayList<>();
}
