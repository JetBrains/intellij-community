package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicMethodDialog extends DynamicDialog{
  public DynamicMethodDialog(Project project, DynamicVirtualMethod virtualMethod, GrReferenceExpression referenceExpression) {
    super(project, virtualMethod, referenceExpression);
  }
}
