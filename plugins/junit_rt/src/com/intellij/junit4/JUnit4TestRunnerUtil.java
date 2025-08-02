// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.junit4;

import com.intellij.junit3.TestRunnerUtil;
import junit.framework.TestCase;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

public final class JUnit4TestRunnerUtil {

  public static Request buildRequest(String[] suiteClassNames, final String programParameters, boolean notForked) {
    if (suiteClassNames.length == 0) {
      return null;
    }
    ArrayList<Class<?>> result = new ArrayList<>();
    for (String suiteClassName : suiteClassNames) {
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        try {
          final Map<String, Set<String>> classMethods = new HashMap<>();
          try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(suiteClassName.substring(1)),
                                                                                StandardCharsets.UTF_8))) {
            final String packageName = reader.readLine();
            if (packageName == null) return null;

            final String categoryName = reader.readLine();
            final Class<?> category = categoryName != null && !categoryName.isEmpty() ? loadTestClass(categoryName) : null;
            final String filters = reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
              String className = line;
              final int idx = line.indexOf(',');
              if (idx != -1) {
                className = line.substring(0, idx);
                Set<String> methodNames = classMethods.get(className);
                if (methodNames == null) {
                  methodNames = new HashSet<>();
                  classMethods.put(className, methodNames);
                }
                methodNames.add(line.substring(idx + 1));
              }
              appendTestClass(result, className);
            }
            String suiteName = packageName.isEmpty() ? "<default package>" : packageName;
            Class<?>[] classes = getArrayOfClasses(result);
            if (classes.length == 0) {
              System.out.println(TestRunnerUtil.testsFoundInPackageMessage(0, suiteName));
              return null;
            }
            Request allClasses;
            try {
              Class.forName("org.junit.runner.Computer");
              allClasses = JUnit46ClassesRequestBuilder.getClassesRequest(suiteName, classes, classMethods, category);
            }
            catch (ClassNotFoundException | NoSuchMethodError e) {
              allClasses = getClassRequestsUsing44API(suiteName, classes);
            }

            return classMethods.isEmpty() ? allClasses : allClasses.filterWith(new Filter() {
              @Override
              public boolean shouldRun(Description description) {
                if (description.isTest()) {
                  final Set<String> methods = classMethods.get(JUnit4ReflectionUtil.getClassName(description));
                  if (methods == null) {
                    return true;
                  }
                  String methodName = JUnit4ReflectionUtil.getMethodName(description);
                  if (methods.contains(methodName)) {
                    return true;
                  }
                  if (programParameters != null) {
                    return methodName.endsWith(programParameters) &&
                           methods.contains(methodName.substring(0, methodName.length() - programParameters.length()));
                  }

                  final Class<?> testClass = description.getTestClass();
                  if (testClass != null) {
                    final RunWith classAnnotation = testClass.getAnnotation(RunWith.class);
                    if (classAnnotation != null && isParameterized(methodName, testClass)) {
                      final int idx = methodName.indexOf("[");
                      if (idx > -1) {
                        return methods.contains(methodName.substring(0, idx));
                      }
                    }
                  }
                  return false;
                }
                return true;
              }

              @Override
              public String describe() {
                return "Tests";
              }
            });
          }
        }
        catch (IOException e) {
          e.printStackTrace();
          System.exit(1);
        }
      }
      else {
        int index = suiteClassName.indexOf(',');
        if (index != -1) {
          final Class<?> clazz = loadTestClass(suiteClassName.substring(0, index));
          final String methodName = suiteClassName.substring(index + 1);
          final RunWith clazzAnnotation = clazz.getAnnotation(RunWith.class);
          final Description testMethodDescription = Description.createTestDescription(clazz, methodName);
          if (clazzAnnotation == null) { //do not override external runners
            try {
              final Method method = clazz.getMethod(methodName);
              if (notForked && (method.getAnnotation(Ignore.class) != null || clazz.getAnnotation(Ignore.class) != null)) { //override ignored case only
                final Request classRequest = JUnit45ClassesRequestBuilder.createIgnoreIgnoredClassRequest(clazz, true);
                final Filter ignoredTestFilter = Filter.matchMethodDescription(testMethodDescription);
                return classRequest.filterWith(new Filter() {
                  @Override
                  public boolean shouldRun(Description description) {
                    return ignoredTestFilter.shouldRun(description);
                  }

                  @Override
                  public String describe() {
                    return "Ignored " + methodName;
                  }
                });
              }
            }
            catch (Throwable ignored) {
              //return simple method runner
            }
          }
          else {
            final Request request = getParameterizedRequest(programParameters, methodName, clazz, clazzAnnotation);
            if (request != null) {
              return request;
            }
          }
          try {
            if (!methodName.equals("suite")) {
              clazz.getMethod("suite"); // check method existence
              return Request.classWithoutSuiteMethod(clazz).filterWith(testMethodDescription);
            }
          }
          catch (Throwable e) {
            //ignore
          }

          final Filter methodFilter;
          try {
            methodFilter = Filter.matchMethodDescription(testMethodDescription);
          }
          catch (NoSuchMethodError e) {
            return Request.method(clazz, methodName);
          }
          return Request.aClass(clazz).filterWith(new Filter() {
            @Override
            public boolean shouldRun(Description description) {
              if (description.isTest() && description.getDisplayName().startsWith("warning(junit.framework.TestSuite$")) {
                return true;
              }

              if (description.isTest() && isParameterizedMethodName(description.getMethodName(), methodName)) {
                return true;
              }

              return methodFilter.shouldRun(description);
            }

            @Override
            public String describe() {
              return methodFilter.describe();
            }
          });
        }
        else if (programParameters != null && suiteClassNames.length == 1) {
          final Class<?> clazz = loadTestClass(suiteClassName);
          if (clazz != null) {
            final RunWith clazzAnnotation = clazz.getAnnotation(RunWith.class);
            final Request request = getParameterizedRequest(programParameters, null, clazz, clazzAnnotation);
            if (request != null) {
              return request;
            }
          }
        }
        appendTestClass(result, suiteClassName);
      }
    }

    if (result.size() == 1) {
      final Class<?> clazz = result.get(0);
      try {
        if (clazz.getAnnotation(Ignore.class) != null) { //override ignored case only
          return JUnit45ClassesRequestBuilder.createIgnoreIgnoredClassRequest(clazz, false);
        }
      }
      catch (ClassNotFoundException e) {
        //return simple class runner
      }
      return Request.aClass(clazz);
    }
    return Request.classes(getArrayOfClasses(result));
  }

  private static boolean isParameterized(final String methodName,
                                         final Class<?> clazz) {
    final RunWith clazzAnnotation = clazz.getAnnotation(RunWith.class);
    if (clazzAnnotation != null && Parameterized.class.isAssignableFrom(clazzAnnotation.value())) {
      return true;
    }
    if (methodName != null) {
      for (Method method : clazz.getDeclaredMethods()) {
        if (methodName.equals(method.getName()) && method.getParameterTypes().length > 0) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean hasAnnotatedPublicMethod(Class<?> clazz,
                                                 String name,
                                                 Class<?>[] parameterTypes,
                                                 Class<? extends Annotation> annotationClass) {
    try {
      if (clazz.getMethod(name, parameterTypes).isAnnotationPresent(annotationClass)) {
        return true;
      }
    }
    catch (NoSuchMethodException ignore) {
    }
    Class<?> sc = clazz.getSuperclass();
    if (sc != null && hasAnnotatedPublicMethod(sc, name, parameterTypes, annotationClass)) {
      return true;
    }
    for (Class<?> intf : clazz.getInterfaces()) {
      if (hasAnnotatedPublicMethod(intf, name, parameterTypes, annotationClass)) {
        return true;
      }
    }
    return false;
  }

  private static Request getParameterizedRequest(final String parameterString,
                                                 final String methodName,
                                                 Class<?> clazz,
                                                 RunWith clazzAnnotation) {
    if (clazzAnnotation == null) return null;

    final Class<? extends Runner> runnerClass = clazzAnnotation.value();
    if (parameterString != null || isParameterized(methodName, clazz)) {
      try {
        if (methodName != null) {
          try {
            final Method method = clazz.getMethod(methodName);
            if (!hasAnnotatedPublicMethod(clazz, methodName, method.getParameterTypes(), Test.class) && TestCase.class.isAssignableFrom(clazz)) {
              return Request.runner(JUnit45ClassesRequestBuilder.createIgnoreAnnotationAndJUnit4ClassRunner(clazz));
            }
          }
          catch (NoSuchMethodException ignore) {
          }
        }
        Class.forName("org.junit.runners.BlockJUnit4ClassRunner"); //ignore for junit4.4 and <
        final Constructor<? extends Runner> runnerConstructor = runnerClass.getConstructor(Class.class);
        return Request.runner(runnerConstructor.newInstance(clazz)).filterWith(new Filter() {
          @Override
          public boolean shouldRun(Description description) {
            final String descriptionMethodName = description.getMethodName();
            //filter by params
            if (parameterString != null && descriptionMethodName != null && !descriptionMethodName.endsWith(parameterString)) {
              return false;
            }

            //filter only selected method
            if (methodName != null && descriptionMethodName != null &&
                !descriptionMethodName.equals(methodName) && //If fork mode is used, a parameter is included in the name itself
                !isParameterizedMethodName(descriptionMethodName, methodName)) {
              return false;
            }
            return true;
          }

          @Override
          public String describe() {
            if (parameterString == null) {
              return methodName + " with any parameter";
            }
            if (methodName == null) {
              return "Parameter " + parameterString + " for any method";
            }
            return methodName + " with parameter " + parameterString;
          }
        });
      }
      catch (Throwable throwable) {
        //return simple method runner
      }
    }
    return null;
  }

  private static boolean isParameterizedMethodName(String parameterizedMethodName, String baseMethodName) {
    return parameterizedMethodName.startsWith(baseMethodName) &&
           //methodName[ valid for any parameter for the current method.
           parameterizedMethodName.length() > baseMethodName.length() &&
           parameterizedMethodName.substring(baseMethodName.length()).trim().startsWith("[");
  }

  private static Request getClassRequestsUsing44API(String suiteName, Class<?>[] classes) {
    Request allClasses;
    try {
      Class.forName("org.junit.internal.requests.ClassesRequest");
      allClasses = JUnit4ClassesRequestBuilder.getClassesRequest(suiteName, classes);
    }
    catch (ClassNotFoundException e1) {
      allClasses = JUnit45ClassesRequestBuilder.getClassesRequest(suiteName, classes);
    }
    return allClasses;
  }

  private static void appendTestClass(List<Class<?>> result, String className) {
    final Class<?> aClass = loadTestClass(className);
    if (!result.contains(aClass)) {  //do not append classes twice: rerun failed tests from one test suite
      result.add(aClass);
    }
  }

  private static Class<?>[] getArrayOfClasses(List<Class<?>> result) {
    //noinspection SSBasedInspection
    return result.toArray(new Class[0]);
  }

  private static Class<?> loadTestClass(String suiteClassName) {
    try {
      return Class.forName(suiteClassName, false, JUnit4TestRunnerUtil.class.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      System.err.print(MessageFormat.format(ResourceBundle.getBundle("messages.RuntimeBundle").getString("junit.class.not.found"), clazz));
      System.exit(1);
    }
    catch (Throwable e) {
      System.err.println(MessageFormat.format(ResourceBundle.getBundle("messages.RuntimeBundle").getString("junit.cannot.instantiate.tests"),
                                              e.toString()));
      System.exit(1);
    }
    return null;
  }
}
