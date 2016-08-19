package com.intellij.remoteServer.impl.configuration;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class RemoteServersManagerState {
  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public List<RemoteServerState> myServers = new ArrayList<>();
}
