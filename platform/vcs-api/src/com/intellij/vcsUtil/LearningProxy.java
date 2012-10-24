/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.vcsUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/19/12
 * Time: 12:42 PM
 */
public abstract class LearningProxy<T, E extends Throwable> {
  private final static Map<String, Object> ourDefaultValues = new HashMap<String, Object>();
  static {
    ourDefaultValues.put("byte", new Byte((byte) 0));
    ourDefaultValues.put("char", new Character('m'));
    ourDefaultValues.put("double", new Double(1));
    ourDefaultValues.put("float", new Float(1f));
    ourDefaultValues.put("int", new Integer(0));
    ourDefaultValues.put("long", new Long(0));
    ourDefaultValues.put("short", new Short((short) 0));
    ourDefaultValues.put("boolean", Boolean.FALSE);
    ourDefaultValues.put("void", null);
  }
  private final Set<MethodDescriptor> myTrackedMethods;
  private final InvocationHandler myLearn;

  public LearningProxy() {
    myTrackedMethods = new HashSet<MethodDescriptor>();
    myLearn = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        myTrackedMethods.add(new MethodDescriptor(method));
        final Class<?> returnType = method.getReturnType();
        if (returnType.isPrimitive()) {
          return ourDefaultValues.get(returnType.getName());
        }
        return null;
      }
    };
  }

  protected abstract void onBefore() throws E;
  protected abstract void onAfter() throws E;

  public <T> T create(Class<T> clazz, final T t) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final MethodDescriptor current = new MethodDescriptor(method);
            if (myTrackedMethods.contains(current)) {
              try {
                onBefore();
                method.setAccessible(true);
                return method.invoke(t, args);
              } finally {
                onAfter();
              }
            }
            return method.invoke(t, args);
          }
        });
  }

  public <T> T learn(Class<T> clazz) {
    return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz}, myLearn);
  }

  private static class MethodDescriptor {
    @NotNull
    private final String myMethodName;
    @NotNull
    private final List<String> myParameters;

    private MethodDescriptor(final Method method) {
      myMethodName = method.getName();
      final Class<?>[] types = method.getParameterTypes();
      myParameters = new ArrayList<String>(types.length);
      for (Class<?> type : types) {
        myParameters.add(type.getName());
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MethodDescriptor that = (MethodDescriptor)o;

      if (!myMethodName.equals(that.myMethodName)) return false;
      if (!myParameters.equals(that.myParameters)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myMethodName.hashCode();
      result = 31 * result + myParameters.hashCode();
      return result;
    }
  }
}
