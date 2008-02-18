package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicProperty;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicVirtualElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicPropertyDialog extends DynamicDialog {
  public DynamicPropertyDialog(Project project, DynamicVirtualProperty virtualProperty, GrReferenceExpression referenceExpression) {
    super(project, virtualProperty, referenceExpression);
  }
}
