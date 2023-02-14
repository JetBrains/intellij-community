// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;

import java.util.*;

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

  public DslPointcut<T, V> and(final DslPointcut<T, V> next) {
    final DslPointcut<T, V> first = this;
    return new DslPointcut<>() {
      @Override
      List<V> matches(T src, ProcessingContext context) {
        final List<V> vs1 = first.matches(src, context);
        if (vs1 == null) return null;

        final List<V> vs2 = next.matches(src, context);
        if (vs2 == null) return null;

        final List<V> result = new ArrayList<>(vs1);
        result.retainAll(new HashSet<>(vs2));
        return result;
      }

      @Override
      boolean operatesOn(Class c) {
        return first.operatesOn(c) && next.operatesOn(c);
      }
    };
  }

  public DslPointcut<T, V> or(final DslPointcut<T, V> next) {
    final DslPointcut<T, V> first = this;
    return new DslPointcut<>() {
      @Override
      List<V> matches(T src, ProcessingContext context) {
        final List<V> vs1 = first.matches(src, context);
        final List<V> vs2 = next.matches(src, context);

        if (vs1 == null && vs2 == null) return null;

        final Set<V> result = new LinkedHashSet<>();
        if (vs1 != null) {
          result.addAll(vs1);
        }
        if (vs2 != null) {
          result.addAll(vs2);
        }
        return new ArrayList<>(result);
      }

      @Override
      boolean operatesOn(Class c) {
        return first.operatesOn(c) && next.operatesOn(c);
      }
    };
  }
  public DslPointcut<T, V> bitwiseNegate() {
    final DslPointcut<T, V> base = this;
    return new DslPointcut<>() {
      @Override
      List<V> matches(T src, ProcessingContext context) {
        return base.matches(src, context) == null ? Collections.emptyList() : null;
      }

      @Override
      boolean operatesOn(Class c) {
        return base.operatesOn(c);
      }
    };
  }


  public static DslPointcut<GdslType, GdslType> subType(final Object arg) {
    return new DslPointcut<>() {

      @Override
      List<GdslType> matches(GdslType src, ProcessingContext context) {
        final PsiFile placeFile = context.get(GdslUtil.INITIAL_CONTEXT).justGetPlaceFile();
        if (ClassContextFilter.isSubtype(src.psiType, placeFile, (String)arg)) {
          return Collections.singletonList(src);
        }
        return null;
      }

      @Override
      boolean operatesOn(Class c) {
        return GdslType.class == c;
      }
    };

  }

  public static DslPointcut<GroovyClassDescriptor, GdslType> currentType(final Object arg) {
    final DslPointcut<GdslType,?> inner;
    if (arg instanceof String) {
      inner = subType(arg);
    } else {
      inner = (DslPointcut<GdslType, ?>)arg;
      assert inner.operatesOn(GdslType.class) : "The argument to currentType should be a pointcut working with types, e.g. subType";
    }

    return new DslPointcut<>() {

      @Override
      List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context) {
        final GdslType currentType = new GdslType(src.getPsiType());
        if (inner.matches(currentType, context) != null) {
          return Collections.singletonList(currentType);
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
    return new DslPointcut<>() {
      @Override
      List<GdslType> matches(GroovyClassDescriptor src, ProcessingContext context) {
        List<GdslType> result = new ArrayList<>();
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

  public static DslPointcut<Object, String> name(final Object arg) {
    return new DslPointcut<>() {
      @Override
      List<String> matches(Object src, ProcessingContext context) {
        if (src instanceof GdslType) {
          return arg.equals(((GdslType)src).getName()) ? Collections.singletonList((String)arg) : null;
        }
        if (src instanceof GdslMethod) {
          return arg.equals(((GdslMethod)src).getName()) ? Collections.singletonList((String)arg) : null;
        }
        return Collections.emptyList();
      }

      @Override
      boolean operatesOn(Class c) {
        return c == GdslType.class || c == GdslMethod.class;
      }
    };

  }

  public static DslPointcut<GroovyClassDescriptor, GdslMethod> enclosingMethod(final Object arg) {
    final DslPointcut<? super GdslMethod,?> inner;
    if (arg instanceof String) {
      inner = name(arg);
    } else {
      inner = (DslPointcut<GdslMethod, ?>)arg;
      assert inner.operatesOn(GdslMethod.class) : "The argument to enclosingMethod should be a pointcut working with methods, e.g. name";
    }

    return new DslPointcut<>() {
      @Override
      List<GdslMethod> matches(GroovyClassDescriptor src, ProcessingContext context) {
        List<GdslMethod> result = new ArrayList<>();
        PsiElement place = src.getPlace();
        while (true) {
          final PsiMethod method = PsiTreeUtil.getContextOfType(place, PsiMethod.class);
          if (method == null) {
            break;
          }
          final GdslMethod wrapper = new GdslMethod(method);
          if (inner.matches(wrapper, context) != null) {
            result.add(wrapper);
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
    assert ((Map<?, ?>)arg).size() == 1;
    final String name = (String)((Map)arg).keySet().iterator().next();
    final DslPointcut pct = (DslPointcut)((Map)arg).values().iterator().next();

    return new DslPointcut() {
      @Override
      List matches(Object src, ProcessingContext context) {
        final List result = pct.matches(src, context);
        if (result != null) {
          Map<String, List> map = context.get(BOUND);
          if (map == null) {
            context.put(BOUND, map = new HashMap<>());
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
