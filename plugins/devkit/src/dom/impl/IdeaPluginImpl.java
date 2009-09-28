package org.jetbrains.idea.devkit.dom.impl;

import org.jetbrains.idea.devkit.dom.IdeaPlugin;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 06.12.2007
*/
public abstract class IdeaPluginImpl implements IdeaPlugin {
  public String getPluginId() {
    final String id = getId().getStringValue();
    if (id != null) {
      return id;
    }
    return getName().getStringValue();
  }
}
