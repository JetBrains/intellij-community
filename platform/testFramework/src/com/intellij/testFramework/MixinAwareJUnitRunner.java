// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import org.junit.internal.MethodSorter;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * JUnit4 test runner that supports test methods declared in interfaces
 */
public class MixinAwareJUnitRunner extends BlockJUnit4ClassRunner {
  public MixinAwareJUnitRunner(Class<?> clazz) throws InitializationError {
    super(clazz);
  }

  @Override
  protected TestClass createTestClass(Class<?> testClass) {
    return new MixinAwareTestClass(testClass);
  }

  static class MixinAwareTestClass extends TestClass {
    MixinAwareTestClass(Class<?> clazz) {
      super(clazz);
    }

    @Override
    protected void scanAnnotatedMembers(Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations,
                                        Map<Class<? extends Annotation>, List<FrameworkField>> fieldsForAnnotations) {
      super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
      scanAnnotatedMethodsInInterfaces(getJavaClass(), methodsForAnnotations);
    }

    private static void scanAnnotatedMethodsInInterfaces(Class<?> clazz,
                                                         Map<Class<? extends Annotation>, List<FrameworkMethod>> methodsForAnnotations) {
      for (Class<?> superInterface : clazz.getInterfaces()) {
        for (Method eachMethod : MethodSorter.getDeclaredMethods(superInterface)) {
          addToAnnotationLists(new FrameworkMethod(eachMethod), methodsForAnnotations);
        }
        scanAnnotatedMethodsInInterfaces(superInterface, methodsForAnnotations);
      }
      Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        scanAnnotatedMethodsInInterfaces(superClass, methodsForAnnotations);
      }
    }
  }
}
