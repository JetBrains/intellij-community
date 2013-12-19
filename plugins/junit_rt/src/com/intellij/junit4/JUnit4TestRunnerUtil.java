/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.junit.Ignore;
import org.junit.internal.AssumptionViolatedException;
import org.junit.internal.requests.ClassRequest;
import org.junit.internal.runners.model.EachTestNotifier;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized;
import org.junit.runners.model.FrameworkMethod;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.*;

public class JUnit4TestRunnerUtil {
  /**
   * @noinspection HardCodedStringLiteral
   */
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  public static Request buildRequest(String[] suiteClassNames, boolean notForked) {
    if (suiteClassNames.length == 0) {
      return null;
    }
    Vector result = new Vector();
    for (int i = 0; i < suiteClassNames.length; i++) {
      String suiteClassName = suiteClassNames[i];
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        try {
          final Map classMethods = new HashMap();
          BufferedReader reader = new BufferedReader(new FileReader(suiteClassName.substring(1)));
          try {
            final String packageName = reader.readLine();
            if (packageName == null) return null;
            String line;

            while ((line = reader.readLine()) != null) {
              String className = line;
              final int idx = line.indexOf(',');
              if (idx != -1) {
                className = line.substring(0, idx);
                Set methodNames = (Set)classMethods.get(className);
                if (methodNames == null) {
                  methodNames = new HashSet();
                  classMethods.put(className, methodNames);
                }
                methodNames.add(line.substring(idx + 1));

              }
              appendTestClass(result, className);
            }
            String suiteName = packageName.length() == 0 ? "<default package>": packageName;
            Class[] classes = getArrayOfClasses(result);
            if (classes.length == 0) {
              System.out.println(TestRunnerUtil.testsFoundInPackageMesage(0, suiteName));
              return null;
            }
            Request allClasses;
            try {
              Class.forName("org.junit.runner.Computer");
              allClasses = JUnit46ClassesRequestBuilder.getClassesRequest(suiteName, classes, classMethods);
            }
            catch (ClassNotFoundException e) {
              allClasses = getClassRequestsUsing44API(suiteName, classes);
            }
            catch (NoSuchMethodError e) {
              allClasses = getClassRequestsUsing44API(suiteName, classes);
            }

            return classMethods.isEmpty() ? allClasses : allClasses.filterWith(new Filter() {
              public boolean shouldRun(Description description) {
                if (description.isTest()) {
                  final Set methods = (Set)classMethods.get(JUnit4ReflectionUtil.getClassName(description));
                  return methods == null || methods.contains(JUnit4ReflectionUtil.getMethodName(description));
                }
                return true;
              }

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
          final Class clazz = loadTestClass(suiteClassName.substring(0, index));
          final String methodName = suiteClassName.substring(index + 1);
          final RunWith clazzAnnotation = (RunWith)clazz.getAnnotation(RunWith.class);
          if (clazzAnnotation == null) { //do not override external runners
            try {
              final Method method = clazz.getMethod(methodName, null);
              if (method != null && notForked && (method.getAnnotation(Ignore.class) != null || clazz.getAnnotation(Ignore.class) != null)) { //override ignored case only
                final Request classRequest = createIgnoreIgnoredClassRequest(clazz, true);
                final Filter ignoredTestFilter = Filter.matchMethodDescription(Description.createTestDescription(clazz, methodName));
                return classRequest.filterWith(new Filter() {
                  public boolean shouldRun(Description description) {
                    return ignoredTestFilter.shouldRun(description);
                  }

                  public String describe() {
                    return "Ignored " + methodName;
                  }
                });
              }
            }
            catch (Exception ignored) {
              //return simple method runner
            }
          } else {
            final Class runnerClass = clazzAnnotation.value();
            if (runnerClass.isAssignableFrom(Parameterized.class)) {
              try {
                Class.forName("org.junit.runners.BlockJUnit4ClassRunner"); //ignore for junit4.4 and <
                return Request.runner(new ParameterizedMethodRunner(clazz, methodName));
              }
              catch (Throwable throwable) {
                //return simple method runner
              }
            }
          }
          try {
            if (clazz.getMethod("suite", new Class[0]) != null && !methodName.equals("suite")) {
              return Request.classWithoutSuiteMethod(clazz).filterWith(Description.createTestDescription(clazz, methodName));
            }
          }
          catch (Throwable e) {
            //ignore
          }
          return Request.method(clazz, methodName);
        }
        appendTestClass(result, suiteClassName);
      }
    }

    if (result.size() == 1) {
      final Class clazz = (Class)result.get(0);
      try {
        if (clazz.getAnnotation(Ignore.class) != null) { //override ignored case only
          return createIgnoreIgnoredClassRequest(clazz, false);
        }
      }
      catch (ClassNotFoundException e) {
        //return simple class runner
      }
      return Request.aClass(clazz);
    }
    return Request.classes(getArrayOfClasses(result));
  }

  private static Request createIgnoreIgnoredClassRequest(final Class clazz, final boolean recursively) throws ClassNotFoundException {
    Class.forName("org.junit.runners.BlockJUnit4ClassRunner"); //ignore IgnoreIgnored for junit4.4 and <
    return new ClassRequest(clazz) {
      public Runner getRunner() {
        try {
          return new IgnoreIgnoredTestJUnit4ClassRunner(clazz, recursively);
        }
        catch (Exception ignored) {
          //return super runner
        }
        return super.getRunner();
      }
    };
  }

  private static Request getClassRequestsUsing44API(String suiteName, Class[] classes) {
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

  private static void appendTestClass(Vector result, String className) {
    final Class aClass = loadTestClass(className);
    if (!result.contains(aClass)) {  //do not append classes twice: rerun failed tests from one test suite
      result.addElement(aClass);
    }
  }

  private static Class[] getArrayOfClasses(Vector result) {
    Class[] classes = new Class[result.size()];
    for (int i = 0; i < result.size(); i++) {
      classes[i] = (Class)result.get(i);
    }
    return classes;
  }

  private static Class loadTestClass(String suiteClassName) {
    try {
      return Class.forName(suiteClassName, false, JUnit4TestRunnerUtil.class.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      System.err.print(MessageFormat.format(ourBundle.getString("junit.class.not.found"), new Object[]{clazz}));
      System.exit(1);
    }
    catch (Exception e) {
      System.err.println(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e.toString()}));
      System.exit(1);
    }
    return null;
  }

  public static String testsFoundInPackageMesage(int testCount, String name) {
    return MessageFormat.format(ourBundle.getString("tests.found.in.package"), new Object[]{new Integer(testCount), name});
  }


  private static class IgnoreIgnoredTestJUnit4ClassRunner extends BlockJUnit4ClassRunner {
    private final boolean myRecursively;

    public IgnoreIgnoredTestJUnit4ClassRunner(Class clazz, boolean recursively) throws Exception {
      super(clazz);
      myRecursively = recursively;
    }

    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
      if (!myRecursively){
        super.runChild(method, notifier);
        return;
      }
      final Description description = describeChild(method);
      final EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
      eachNotifier.fireTestStarted();
      try {
        methodBlock(method).evaluate();
      }
      catch (AssumptionViolatedException e) {
        eachNotifier.addFailedAssumption(e);
      }
      catch (Throwable e) {
        eachNotifier.addFailure(e);
      }
      finally {
        eachNotifier.fireTestFinished();
      }
    }
  }

  private static class ParameterizedMethodRunner extends Parameterized {
    private final String myMethodName;

    public ParameterizedMethodRunner(Class clazz, String methodName) throws Throwable {
      super(clazz);
      myMethodName = methodName;
    }

    protected List getChildren() {
      final List children = super.getChildren();
      for (int i = 0; i < children.size(); i++) {
        try {
          final BlockJUnit4ClassRunner child = (BlockJUnit4ClassRunner)children.get(i);
          final Method getChildrenMethod = BlockJUnit4ClassRunner.class.getDeclaredMethod("getChildren", new Class[0]);
          getChildrenMethod.setAccessible(true);
          final List list = (List)getChildrenMethod.invoke(child, new Object[0]);
          for (Iterator iterator = list.iterator(); iterator.hasNext(); ) {
            final FrameworkMethod description = (FrameworkMethod)iterator.next();
            if (!description.getName().equals(myMethodName)) {
              iterator.remove();
            }
          }
        }
        catch (Exception e) {
         e.printStackTrace();
        }
      }
      return children;
    }
  }
}
