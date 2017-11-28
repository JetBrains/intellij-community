package org.jetbrains.plugins.groovy.griffon;

import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.mvc.MvcModuleBuilder;

import javax.swing.*;

/**
 * @author peter
 */
public class GriffonModuleBuilder extends MvcModuleBuilder {
  public GriffonModuleBuilder() {
    super(GriffonFramework.getInstance());
  }

  @Override
  public Icon getNodeIcon() {
    return JetgroovyIcons.Griffon.Griffon;
  }
}
