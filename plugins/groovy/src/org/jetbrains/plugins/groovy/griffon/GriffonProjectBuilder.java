package org.jetbrains.plugins.groovy.griffon;

import org.jetbrains.plugins.groovy.mvc.MvcCreateFromSourcesMode;
import org.jetbrains.plugins.groovy.mvc.MvcModuleBuilder;

/**
 * @author peter
 */
public class GriffonProjectBuilder extends MvcCreateFromSourcesMode {
  public GriffonProjectBuilder() {
    super(GriffonFramework.getInstance());
  }

  @Override
  protected MvcModuleBuilder createModuleBuilder() {
    return new GriffonModuleBuilder();
  }
}
