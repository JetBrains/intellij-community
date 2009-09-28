package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyOverlyNestedMethodInspection extends GroovyMethodMetricInspection {

  @NotNull
  public String getDisplayName() {
    return "Overly nested method";
  }

  @NotNull
  public String getGroupDisplayName() {
    return METHOD_METRICS;
  }

  protected int getDefaultLimit() {
    return 5;
  }

  protected String getConfigurationLabel() {
    return "Maximum nesting depth:";
  }

  public String buildErrorString(Object... args) {
    return "Method '#ref' is overly nested ( nesting depth =" + args[0] + '>' + args[1] + ')';
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    public void visitMethod(GrMethod grMethod) {
      super.visitMethod(grMethod);
      final int limit = getLimit();
      final NestingDepthVisitor visitor = new NestingDepthVisitor();
      final GrOpenBlock body = grMethod.getBlock();
      if (body == null) {
        return;
      }
      body.accept(visitor);
      final int nestingDepth = visitor.getMaximumDepth();
      if (nestingDepth <= limit) {
        return;
      }
      registerMethodError(grMethod, nestingDepth, limit);
    }
  }
}