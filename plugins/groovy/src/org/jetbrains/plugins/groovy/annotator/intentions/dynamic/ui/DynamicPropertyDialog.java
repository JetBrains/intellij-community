package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.module.Module;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicPropertyDialog extends DynamicDialog {
  public DynamicPropertyDialog(Module module, GrReferenceExpression referenceExpression) {
    super(module, referenceExpression);

    setTitle(GroovyBundle.message("add.dynamic.property"));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.property.type"));
  }
}
