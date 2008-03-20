package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicPropertyDialog extends DynamicDialog {
  public DynamicPropertyDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression);

    setTitle(GroovyBundle.message("add.dynamic.property"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.property.type"));
  }
}
