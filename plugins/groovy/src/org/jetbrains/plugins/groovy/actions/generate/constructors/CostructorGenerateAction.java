package org.jetbrains.plugins.groovy.actions.generate.constructors;

import org.jetbrains.plugins.groovy.actions.generate.GrBaseGenerateAction;

/**
 * User: Dmitry.Krasilschikov
 * Date: 21.05.2008
 */
public class CostructorGenerateAction extends GrBaseGenerateAction {
  public CostructorGenerateAction() {
    super(new ConstructorGenerateHandler());
  }
}
