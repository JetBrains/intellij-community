/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;

public class JUnit4TestRunnerUtil {

  public static Request buildRequest(String[] suiteClassNames, final String name, boolean notForked) {
    if (suiteClassNames.length == 0) {
      return null;
    }
    ArrayList<Class<?>> result = new ArrayList<Class<?>>();
    for (String suiteClassName : suiteClassNames) {
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        try {
          final Map<String, Set<String>> classMethods = new HashMap<String, Set<String>>();
          BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(suiteClassName.substring(1)), "UTF-8"));
          try {
            final String packageName = reader.readLine();
            if (packageName == null) return null;

            final String categoryName = reader.readLine();
            final Class<?> category = categoryName != null && categoryName.length() > 0 ? loadTestClass(categoryName) : null;
            final String filters = reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
              String className = line;
              final int idx = line.indexOf(',');
              if (idx != -1) {
                className = line.substring(0, idx);
                Set<String> methodNames = classMethods.get(className);
                if (methodNames == null) {
                  methodNames = new HashSet<String>();
                  classMethods.put(className, methodNames);
                }
                methodNames.add(line.substring(idx + 1));
              }
              appendTestClass(result, className);
            }
            String suiteName = packageName.length() == 0 ? "<default package>" : packageName;
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
            catch (ClassNotFoundException e) {
              allClasses = getClassRequestsUsing44API(suiteName, classes);
            }
            catch (NoSuchMethodError e) {
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
                  if (name != null) {
                    return methodName.endsWith(name) &&
                           methods.contains(methodName.substring(0, methodName.length() - name.length()));
                  }

                  final Class<?> testClass = description.getTestClass();
                  if (testClass != null) {
                    final RunWith classAnnotation = testClass.getAnnotation(RunWith.class);
                    if (classAnnotation != null && Parameterized.class.isAssignableFrom(classAnnotation.value())) {
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
          finally {
            reader.close();
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
              if (method != null &&
                  notForked &&
                  (method.getAnnotation(Ignore.class) != null || clazz.getAnnotation(Ignore.class) != null)) { //override ignored case only
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
            final Request request = getParameterizedRequest(name, methodName, clazz, clazzAnnotation);
            if (request != null) {
              return request;
            }
          }
          try {
            if (clazz.getMethod("suite") != null && !methodName.equals("suite")) {
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

              return methodFilter.shouldRun(description);
            }

            @Override
            public String describe() {
              return methodFilter.describe();
            }
          });
        }
        else if (name != null && suiteClassNames.length == 1) {
          final Class<?> clazz = loadTestClass(suiteClassName);
          if (clazz != null) {
            final RunWith clazzAnnotation = clazz.getAnnotation(RunWith.class);
            final Request request = getParameterizedRequest(name, null, clazz, clazzAnnotation);
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

  private static Request getParameterizedRequest(final String parameterString,
                                                 final String methodName,
                                                 Class<?> clazz,
                                                 RunWith clazzAnnotation) {
    if (clazzAnnotation == null) return null;

    final Class<? extends Runner> runnerClass = clazzAnnotation.value();
    if (Parameterized.class.isAssignableFrom(runnerClass)) {
      try {
        if (methodName != null) {
          final Method method = clazz.getMethod(methodName);
          if (method != null && !method.isAnnotationPresent(Test.class) && TestCase.class.isAssignableFrom(clazz)) {
            return Request.runner(JUnit45ClassesRequestBuilder.createIgnoreAnnotationAndJUnit4ClassRunner(clazz));
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
                !descriptionMethodName.startsWith(methodName + "[") && //valid for any parameter for current method
                !descriptionMethodName.equals(methodName)) { //if fork mode used, parameter is included in the name itself
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

  private static Request getClassRequestsUsing44API(String suiteName, Class<?>[] classes) {
    Request allClasses;
    try {
      Class.forName("org.junit.internal.requests.ClassesRequest");
      allClasses = JUnit4ClassesRequestBuilder.getClassesRequest(suiteName, classes);
    }
    catch (ClassNotFoundException e1) {
      allClasses  = JUnit45ClassesRequestBuilder.getClassesRequest(suiteName, classes);
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
      System.err.print(MessageFormat.format(ResourceBundle.getBundle("RuntimeBundle").getString("junit.class.not.found"), clazz));
      System.exit(1);
    }
    catch (Exception e) {
      System.err.println(MessageFormat.format(ResourceBundle.getBundle("RuntimeBundle").getString("junit.cannot.instantiate.tests"),
                                              e.toString()));
      System.exit(1);
    }
    return null;
  }
}
