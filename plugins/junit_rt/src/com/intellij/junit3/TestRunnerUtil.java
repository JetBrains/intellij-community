/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.junit3;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import junit.runner.BaseTestRunner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class TestRunnerUtil {
  /** @noinspection HardCodedStringLiteral*/
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  public static Test getTestSuite(JUnit3IdeaTestRunner runner, String[] suiteClassNames){
    if (suiteClassNames.length == 0) {
      return null;
    }
    ArrayList<Test> result = new ArrayList<Test>();
    for (String suiteClassName : suiteClassNames) {
      Test test;
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        String[] classNames;
        String suiteName;
        try {
          BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(suiteClassName.substring(1)), "UTF-8"));
          ArrayList<String> vector;
          try {
            suiteName = reader.readLine();

            reader.readLine(); //category
            reader.readLine();//filters

            vector = new ArrayList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
              vector.add(line);
            }
          }
          finally {
            reader.close();
          }

          // toArray cannot be used here because the class must be compilable with 1.1
          //noinspection SSBasedInspection
          classNames = vector.toArray(new String[0]);
        }
        catch (Exception e) {
          runner.runFailed(MessageFormat.format(ourBundle.getString("junit.runner.error"), e.toString()));
          return null;
        }
        test = new TestAllInPackage2(runner, suiteName, classNames);
      }
      else {
        test = createClassOrMethodSuite(runner, suiteClassName);
        if (test == null) return null;
      }
      result.add(test);
    }
    if (result.size() == 1) {
      return result.get(0);
    }
    else {
      TestSuite suite = new TestSuite();
      for (final Test test : result) {
        suite.addTest(test);
      }
      return suite;
    }
  }

  public static Test createClassOrMethodSuite(JUnit3IdeaTestRunner runner, String suiteClassName) {
    String methodName = null;
    int index = suiteClassName.indexOf(',');
    if (index != -1) {
      methodName = suiteClassName.substring(index + 1);
      suiteClassName = suiteClassName.substring(0, index);
    }

    Class<?> testClass = loadTestClass(runner, suiteClassName);
    if (testClass == null) return null;
    Test test = null;
    if (methodName == null) {
      if (test == null) {
        try {
          Method suiteMethod = testClass.getMethod(BaseTestRunner.SUITE_METHODNAME);
          if (!Modifier.isStatic(suiteMethod.getModifiers())) {
            String message = MessageFormat.format(ourBundle.getString("junit.suite.must.be.static"), testClass.getName());
            System.err.println(message);
            //runFailed(message);
            return null;
          }
          try {
            test = (Test)suiteMethod.invoke(null); // static method 
            if (test == null) {
              return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, 
                                        MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"),
                                                             "method " + suiteClassName + ".suite() evaluates to null"), 
                                        null);
            }
            test = new SuiteMethodWrapper(test, suiteClassName);
          }
          catch (final InvocationTargetException e) {
            final String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"),
                                                        testClass + " " + e.getTargetException().toString());
            //System.err.println(message);
            //runner.runFailed(message);
            runner.clearStatus();
            return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, message, e);
          }
          catch (IllegalAccessException e) {
            String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), testClass + " " + e.toString());
            //System.err.println(message);
            //runner.runFailed(message);
            return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, message, e);
          }
        }
        catch (Throwable e) {
          // try to extract a test suite automatically
          runner.clearStatus();
          test = new TestSuite(testClass);
        }
      }
    }
    else {
      test = createMethodSuite(runner, testClass, methodName);
    }
    return test;
  }

  private static Class<?> loadTestClass(JUnit3IdeaTestRunner runner, String suiteClassName) {
    try {
      return Class.forName(suiteClassName, false, TestRunnerUtil.class.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.found"), clazz));
    }
    catch (Exception e) {
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), e.toString()));
    }
    return null;
  }

  private static Test createMethodSuite(JUnit3IdeaTestRunner runner, Class<?> testClass, String methodName) {
    runner.clearStatus();
    try {
      Constructor<?> constructor = testClass.getConstructor(String.class);
      return (Test)constructor.newInstance(methodName);
    }
    catch (NoSuchMethodException e) {
      try {
        Constructor<?> constructor = testClass.getConstructor();
        TestCase test = (TestCase)constructor.newInstance();
        test.setName(methodName);
        return test;
      }
      catch(ClassCastException e1) {
        boolean methodExists;
        try {
          testClass.getMethod(methodName);
          methodExists = true;
        }
        catch (NoSuchMethodException e2) {
          methodExists = false;
        }
        if (!methodExists) {
          String error = MessageFormat.format(ourBundle.getString("junit.method.not.found"), methodName);
          String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), error);
          return new FailedTestCase(testClass, methodName, message, null);
        }
        runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.derived"), testClass.getName()));
        return null;
      }
      catch (Exception e1) {
        String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), e1.toString());
        return new FailedTestCase(testClass, methodName, message, e1);
      }
    }
    catch (Throwable e) {
      String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), e.toString());
      return new FailedTestCase(testClass, methodName, message, e);
    }
  }

  public static String testsFoundInPackageMessage(int testCount, String name) {
    return MessageFormat.format(ourBundle.getString("tests.found.in.package"), new Integer(testCount), name);
  }

  /** @noinspection JUnitTestClassNamingConvention, JUnitTestCaseWithNonTrivialConstructors, JUnitTestCaseWithNoTests */
  public static class FailedTestCase extends TestCase {
    private final String myMethodName;
    private final String myMessage;
    private final Throwable myThrowable;

    public FailedTestCase(final Class<?> testClass, final String methodName, final String message, final Throwable e) {
      super(testClass.getName());
      myMethodName = methodName;
      myMessage = message;
      myThrowable = e;
    }

    public String getMethodName() {
      return myMethodName;
    }

    public String getMessage() {
      return myMessage;
    }

    @Override
    protected void runTest() {
      try {
        throw new RuntimeException(myMessage, myThrowable);
      }
      catch (NoSuchMethodError e) {
        throw new RuntimeException(myMessage);
      }
    }
  }

  public static class SuiteMethodWrapper implements Test {
    private final Test mySuite;
    private final String myClassName;

    public SuiteMethodWrapper(Test suite, String className) {
      mySuite = suite;
      myClassName = className;
    }

    public String getClassName() {
      return myClassName;
    }

    @Override
    public int countTestCases() {
      return mySuite.countTestCases();
    }

    @Override
    public void run(TestResult result) {
      mySuite.run(result);
    }

    public Test getSuite() {
      return mySuite;
    }
  }
}
