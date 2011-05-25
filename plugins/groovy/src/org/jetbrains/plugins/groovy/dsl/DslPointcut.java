package org.jetbrains.plugins.groovy.dsl;

import com.intellij.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;

/**
 * @author peter
 */
public abstract class DslPointcut<T> {

  abstract boolean matches(T t, ProcessingContext context);

  abstract boolean operatesOn(Class c);

  public static DslPointcut<GroovyClassDescriptor> currentType(final Object arg) {
    return new DslPointcut<GroovyClassDescriptor>() {
      @Override
      boolean matches(GroovyClassDescriptor groovyClassDescriptor, ProcessingContext context) {
        return ClassContextFilter.subtypeOf((String)arg).isApplicable(groovyClassDescriptor, context);
      }

      @Override
      boolean operatesOn(Class c) {
        return GroovyClassDescriptor.class == c;
      }
    };
  }

  public static <T> DslPointcut<T> unknownPointcut() {
    return new DslPointcut<T>() {
      @Override
      boolean matches(T t, ProcessingContext context) {
        return true;
      }

      @Override
      boolean operatesOn(Class c) {
        return true;
      }
    };
  }
}
