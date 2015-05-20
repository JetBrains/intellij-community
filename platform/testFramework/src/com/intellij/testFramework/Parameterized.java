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
package com.intellij.testFramework;

import org.junit.runner.Runner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParametersFactory;
import org.junit.runners.parameterized.ParametersRunnerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;

public class Parameterized extends org.junit.runners.Parameterized {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Parameters {
    String name() default "{index}";
  }

  private List<Runner> l;
  
  public Parameterized(Class<?> klass) throws Throwable {
    super(klass);
    FrameworkMethod parametersMethod = getParametersMethod();
    if (parametersMethod != null) {
      Parameters parameters = parametersMethod.getAnnotation(Parameters.class);
      Method declaredMethod =
        org.junit.runners.Parameterized.class.getDeclaredMethod("createRunnersForParameters", Iterable.class, String.class, ParametersRunnerFactory.class);
      declaredMethod.setAccessible(true);
      l = (List<Runner>)declaredMethod.invoke(this, allParameters(klass, parametersMethod), parameters.name(), BlockJUnit4ClassRunnerWithParametersFactory.class.newInstance());
    }
  }


  @Override
  protected List<Runner> getChildren() {
    return l;
  }

  private FrameworkMethod getParametersMethod() throws Exception {
    List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(Parameters.class);
    for (FrameworkMethod each : methods) {
      if (each.isStatic() && each.isPublic()) {
        return each;
      }
    }

    return null;
  }

  private static Iterable<Object[]> allParameters(Class<?> klass, FrameworkMethod parametersMethod) throws Throwable {
    Object parameters = parametersMethod.invokeExplosively(null, klass);
    if (parameters instanceof Iterable) {
      return (Iterable<Object[]>) parameters;
    } else {
      throw new IllegalArgumentException("Wrong return type");
    }
  }


}
