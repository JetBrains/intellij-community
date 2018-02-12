/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.cglib.proxy;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import net.sf.cglib.core.CodeGenerationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;

/**
 * @author peter
 */
public class AdvancedProxy {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.AdvancedProxy");
  public static Method FINALIZE_METHOD;
  public static Method EQUALS_METHOD;
  public static Method HASHCODE_METHOD;
  public static Method TOSTRING_METHOD;

  static {
    try {
      FINALIZE_METHOD = Object.class.getDeclaredMethod("finalize");
      EQUALS_METHOD = Object.class.getDeclaredMethod("equals", Object.class);
      HASHCODE_METHOD = Object.class.getDeclaredMethod("hashCode");
      TOSTRING_METHOD = Object.class.getDeclaredMethod("toString");
    }
    catch (NoSuchMethodException e) {
      LOG.error(e);
    }
  }


  private static final Map<ProxyDescription, Factory> ourFactories = ContainerUtil.createConcurrentWeakValueMap();
  private static final CallbackFilter NO_OBJECT_METHODS_FILTER = new CallbackFilter() {
    public int accept(Method method) {
      if (AdvancedProxy.FINALIZE_METHOD.equals(method)) {
        return 1;
      }

      if ((method.getModifiers() & Modifier.ABSTRACT) != 0) {
        return 0;
      }

      return 1;
    }
  };
  private static final CallbackFilter WITH_OBJECT_METHODS_FILTER = new CallbackFilter() {
    public int accept(Method method) {
      if (AdvancedProxy.FINALIZE_METHOD.equals(method)) {
        return 1;
      }

      if (HASHCODE_METHOD.equals(method) || TOSTRING_METHOD.equals(method) || EQUALS_METHOD.equals(method)) {
        return 0;
      }

      if ((method.getModifiers() & Modifier.ABSTRACT) != 0) {
        return 0;
      }

      return 1;
    }
  };

  public static InvocationHandler getInvocationHandler(Object proxy) {
    return (InvocationHandler)((Factory) proxy).getCallback(0);
  }

  public static <T> T createProxy(final InvocationHandler handler, final Class<T> superClass, final Class... otherInterfaces) {
    return createProxy(superClass, otherInterfaces, handler, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public static <T> T createProxy(final Class<T> superClass, final Class... otherInterfaces) {
    return createProxy(superClass, otherInterfaces, new InvocationHandler() {
      public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        throw new AbstractMethodError(method.toString());
      }
    }, false, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                  final InvocationHandler handler, final Object... constructorArgs) {
    return createProxy(superClass, interfaces, handler, true, constructorArgs);
  }

  public static <T> T createProxy(final Class<T> superClass,
                                  final Class[] interfaces,
                                  final InvocationHandler handler,
                                  final boolean interceptObjectMethods, final Object... constructorArgs) {
    try {
      final Callback[] callbacks = new Callback[]{handler, NoOp.INSTANCE};

      final ProxyDescription key = new ProxyDescription(superClass, interfaces);
      Factory factory = ourFactories.get(key);
      if (factory != null) {
        return (T)factory.newInstance(getConstructorParameterTypes(factory.getClass(), constructorArgs), constructorArgs, callbacks);
      }

      AdvancedEnhancer e = new AdvancedEnhancer();
      e.setInterfaces(interfaces);
      e.setCallbacks(callbacks);
      e.setCallbackFilter(interceptObjectMethods ? WITH_OBJECT_METHODS_FILTER : NO_OBJECT_METHODS_FILTER);
      if (superClass != null) {
        e.setSuperclass(superClass);
        factory = (Factory)e.create(getConstructorParameterTypes(superClass, constructorArgs), constructorArgs);
      }
      else {
        assert constructorArgs.length == 0;
        factory = (Factory)e.create();
      }

      ourFactories.put(key, factory);
      return (T)factory;
    }
    catch (CodeGenerationException e) {
      final Throwable throwable = e.getCause();
      if (throwable instanceof InvocationTargetException) {
        final InvocationTargetException targetException = (InvocationTargetException)throwable;
        final Throwable cause = targetException.getCause();
        ExceptionUtil.rethrowUnchecked(cause);
      }
      ExceptionUtil.rethrowUnchecked(throwable);
      throw e;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Class[] getConstructorParameterTypes(final Class aClass, final Object... constructorArgs) {
    if (constructorArgs.length == 0) return ArrayUtil.EMPTY_CLASS_ARRAY;

    loop: for (final Constructor constructor : aClass.getDeclaredConstructors()) {
      final Class[] parameterTypes = constructor.getParameterTypes();
      if (parameterTypes.length == constructorArgs.length) {
        for (int i = 0; i < parameterTypes.length; i++) {
          Class parameterType = parameterTypes[i];
          final Object constructorArg = constructorArgs[i];
          if (!parameterType.isInstance(constructorArg) && constructorArg != null) {
            continue loop;
          }
        }
        return constructor.getParameterTypes();
      }
    }
    throw new AssertionError("Cannot find constructor for arguments: " + Arrays.asList(constructorArgs));
  }

  private static class ProxyDescription {
    private final Class mySuperClass;
    private final Class[] myInterfaces;

    public ProxyDescription(final Class superClass, final Class[] interfaces) {
      mySuperClass = superClass;
      myInterfaces = interfaces;
    }

    public String toString() {
      return mySuperClass + " " + (myInterfaces != null ? Arrays.asList(myInterfaces) : "");
    }

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final ProxyDescription that = (ProxyDescription)o;

      if (!Arrays.equals(myInterfaces, that.myInterfaces)) return false;
      if (mySuperClass != null ? !mySuperClass.equals(that.mySuperClass) : that.mySuperClass != null) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = (mySuperClass != null ? mySuperClass.hashCode() : 0);
      return result;
    }
  }

}
