package org.jetbrains.plugins.groovy.griffon;

import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.mvc.MvcModuleBuilder;

import javax.swing.*;

/**
 * @author peter
 */
public class GriffonModuleBuilder extends MvcModuleBuilder {
  private static final Icon GRIFFON_ICON_24x24 = JetgroovyIcons.Griffon.Griffon_icon_24x24;

  public GriffonModuleBuilder() {
    super(GriffonFramework.getInstance(), GRIFFON_ICON_24x24);
  }

}
