package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.Key;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;

import java.util.*;

/**
 * @author peter
 */
public abstract class DslPointcut<T,V> {
  public static final DslPointcut UNKNOWN = new DslPointcut() {

    @Override
    List matches(Object src, ProcessingContext context) {
      return Collections.emptyList();
    }

    @Override
    boolean operatesOn(Class c) {
      return true;
    }
  };
  public static Key<Map<String, List>> BOUND = Key.create("gdsl.bound");

  @Nullable
  abstract List<V> matches(T src, ProcessingContext context);

  abstract boolean operatesOn(Class c);

  public static DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg) {
    return new DslPointcut<GroovyClassDescriptor, GdslType>() {

      @Override
      List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context) {
        if (ClassContextFilter.subtypeOf((String)arg).isApplicable(src, context)) {
          return Arrays.asList(new GdslType(ClassContextFilter.findPsiType(src, context)));
        }
        return null;
      }

      @Override
      boolean operatesOn(Class c) {
        return GroovyClassDescriptor.class == c;
      }
    };
  }

  public static DslPointcut bind(final Object arg) {
    assert arg instanceof Map;
    assert ((Map)arg).size() == 1;
    final String name = (String)((Map)arg).keySet().iterator().next();
    final DslPointcut pct = (DslPointcut)((Map)arg).values().iterator().next();

    return new DslPointcut() {
      @Override
      List matches(Object src, ProcessingContext context) {
        final List result = pct.matches(src, context);
        if (result != null) {
          Map<String, List> map = context.get(BOUND);
          if (map == null) {
            context.put(BOUND, map = new HashMap<String, List>());
          }
          map.put(name, result);
        }
        return result;
      }

      @Override
      boolean operatesOn(Class c) {
        return pct.operatesOn(c);
      }
    };
  }

}
