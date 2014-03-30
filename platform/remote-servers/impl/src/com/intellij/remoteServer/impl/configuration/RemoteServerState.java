package com.intellij.remoteServer.impl.configuration;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;

/**
* @author nik
*/
@Tag("remote-server")
public class RemoteServerState {
  @Attribute("name")
  public String myName;
  @Attribute("type")
  public String myTypeId;
  @Tag("configuration")
  public Element myConfiguration;
}
