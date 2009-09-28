package org.jetbrains.plugins.groovy.actions.generate.equals;

import org.jetbrains.plugins.groovy.actions.generate.GrBaseGenerateAction;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.05.2008
 */
public class EqualsGenerateAction extends GrBaseGenerateAction {
  public EqualsGenerateAction() {
    super(new EqualsGenerateHandler());
  }
}
