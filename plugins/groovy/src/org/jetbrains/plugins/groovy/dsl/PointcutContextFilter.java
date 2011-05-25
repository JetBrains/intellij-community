package org.jetbrains.plugins.groovy.dsl;

import com.intellij.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

/**
 * @author peter
 */
public class PointcutContextFilter implements ContextFilter {
  private final DslPointcut<GroovyClassDescriptor> myPointcut;

  public PointcutContextFilter(DslPointcut<GroovyClassDescriptor> pointcut) {
    myPointcut = pointcut;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return myPointcut.matches(descriptor, ctx);
  }
}
