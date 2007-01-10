package com.intellij.ide.plugins;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("helpset")
public class PluginHelpSet {
  @Attribute("file")
  public String file;

  @Attribute("path")
  public String path;
}
