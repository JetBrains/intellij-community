// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * @deprecated This class is used to support the old Kotlin JPS plugin (1.6.*) bundled in Spring 2.*
 * Please don't use it in your code and don't remove (KTIJ-29067)
 */
@Deprecated
@ApiStatus.Internal
public interface Equality<T> {
  boolean equals(T o1, T o2);

  Equality<Object> IDENTITY = createIdentityEquality();
  Equality<Object> CANONICAL = createCanonicalEquality();

  @SuppressWarnings("unchecked")
  static <T> Equality<T> createIdentityEquality() {
    return (Equality<T>)Proxy.newProxyInstance(
      Equality.class.getClassLoader(),
      new Class<?>[]{Equality.class},
      new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("equals".equals(method.getName())) {
            return args[0] == args[1];
          }
          throw new UnsupportedOperationException("Unsupported method: " + method.getName());
        }
      }
    );
  }

  @SuppressWarnings("unchecked")
  static <T> Equality<T> createCanonicalEquality() {
    return (Equality<T>)Proxy.newProxyInstance(
      Equality.class.getClassLoader(),
      new Class<?>[]{Equality.class},
      new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("equals".equals(method.getName())) {
            return Objects.equals(args[0], args[1]);
          }
          throw new UnsupportedOperationException("Unsupported method: " + method.getName());
        }
      }
    );
  }
}
