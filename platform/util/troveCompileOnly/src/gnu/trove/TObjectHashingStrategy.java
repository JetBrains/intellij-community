// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package gnu.trove;

import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @deprecated This class is used to support the old Kotlin JPS plugin (1.6.*) bundled in Spring 2.*
 * Please don't use it in your code and don't remove (KTIJ-29067)
 */
@Deprecated
@ApiStatus.Internal
public interface TObjectHashingStrategy<T> extends Serializable {
  int computeHashCode(T object);

  boolean equals(T o1, T o2);

  TObjectHashingStrategy<Object> IDENTITY = createIdentityStrategy();
  TObjectHashingStrategy<Object> CANONICAL = createCanonicalStrategy();

  @SuppressWarnings("unchecked")
  static <T> TObjectHashingStrategy<T> createIdentityStrategy() {
    return (TObjectHashingStrategy<T>)Proxy.newProxyInstance(
      TObjectHashingStrategy.class.getClassLoader(),
      new Class<?>[]{TObjectHashingStrategy.class},
      new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("computeHashCode".equals(method.getName())) {
            return System.identityHashCode(args[0]);
          }
          else if ("equals".equals(method.getName())) {
            return args[0] == args[1];
          }
          throw new UnsupportedOperationException("Unsupported method: " + method.getName());
        }
      }
    );
  }

  @SuppressWarnings("unchecked")
  static <T> TObjectHashingStrategy<T> createCanonicalStrategy() {
    return (TObjectHashingStrategy<T>)Proxy.newProxyInstance(
      TObjectHashingStrategy.class.getClassLoader(),
      new Class<?>[]{TObjectHashingStrategy.class},
      new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("computeHashCode".equals(method.getName())) {
            return args[0] != null ? args[0].hashCode() : 0;
          }
          else if ("equals".equals(method.getName())) {
            if (args[0] == null) {
              return args[1] == null;
            }
            return args[0].equals(args[1]);
          }
          throw new UnsupportedOperationException("Unsupported method: " + method.getName());
        }
      }
    );
  }
}