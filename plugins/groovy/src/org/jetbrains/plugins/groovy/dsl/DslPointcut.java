package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;

import java.util.*;

/**
 * @author peter
 */
@SuppressWarnings("UnusedDeclaration")
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

  public static DslPointcut<GroovyClassDescriptor, GdslType> enclosingType(final Object arg) {
    return new DslPointcut<GroovyClassDescriptor, GdslType>() {
      @Override
      List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context) {
        List<GdslType> result = new ArrayList<GdslType>();
        PsiElement place = src.getPlace();
        while (true) {
          final PsiClass cls = PsiTreeUtil.getContextOfType(place, PsiClass.class);
          if (cls == null) {
            break;
          }
          if (arg.equals(cls.getQualifiedName())) {
            result.add(new GdslType(JavaPsiFacade.getElementFactory(cls.getProject()).createType(cls)));
          }
          place = cls;
        }
        return result.isEmpty() ? null : result;
      }

      @Override
      boolean operatesOn(Class c) {
        return GroovyClassDescriptor.class == c;
      }
    };
  }

  public static DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg) {
    return new DslPointcut<GroovyClassDescriptor, GdslMethod>() {
      @Override
      List<GdslMethod> matches(GroovyClassDescriptor src, ProcessingContext context) {
        List<GdslMethod> result = new ArrayList<GdslMethod>();
        PsiElement place = src.getPlace();
        while (true) {
          final PsiMethod method = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
          if (method == null) {
            break;
          }
          if (arg.equals(method.getName())) {
            result.add(new GdslMethod());
          }
          place = method;
        }
        return result.isEmpty() ? null : result;
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
