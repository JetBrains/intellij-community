package org.jetbrains.plugins.groovy.griffon;

import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.mvc.MvcModuleBuilder;

/**
 * @author peter
 */
public class GriffonModuleBuilder extends MvcModuleBuilder {
  public GriffonModuleBuilder() {
    super(GriffonFramework.getInstance(), JetgroovyIcons.Griffon.Griffon_icon_24x24);
  }

}
