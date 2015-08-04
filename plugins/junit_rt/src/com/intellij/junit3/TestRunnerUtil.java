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
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.Vector;

public class TestRunnerUtil {
  /** @noinspection HardCodedStringLiteral*/
  private static final ResourceBundle ourBundle = ResourceBundle.getBundle("RuntimeBundle");

  public static Test getTestSuite(JUnit3IdeaTestRunner runner, String[] suiteClassNames){
    if (suiteClassNames.length == 0) {
      return null;
    }
    Vector result = new Vector();
    for (int i = 0; i < suiteClassNames.length; i++) {
      String suiteClassName = suiteClassNames[i];
      Test test;
      if (suiteClassName.charAt(0) == '@') {
        // all tests in the package specified
        String[] classNames;
        String suiteName;
        try {
          BufferedReader reader = new BufferedReader(new FileReader(suiteClassName.substring(1)));
          Vector vector;
          try {
            suiteName = reader.readLine();

            reader.readLine(); //category

            vector = new Vector();
            String line;
            while ((line = reader.readLine()) != null) {
              vector.addElement(line);
            }
          }
          finally {
            reader.close();
          }

          // toArray cannot be used here because the class must be compilable with 1.1
          classNames = new String[vector.size()];
          for (int j = 0; j < classNames.length; j++) {
            classNames[j] = (String)vector.elementAt(j);
          }
        }
        catch (Exception e) {
          runner.runFailed(MessageFormat.format(ourBundle.getString("junit.runner.error"), new Object[] {e.toString()}));
          return null;
        }
        test = new TestAllInPackage2(runner, suiteName, classNames);
      }
      else {
        test = createClassOrMethodSuite(runner, suiteClassName);
        if (test == null) return null;
      }
      result.addElement(test);
    }
    if (result.size() == 1) {
      return (Test)result.elementAt(0);
    }
    else {
      TestSuite suite = new TestSuite();
      for (int i = 0; i < result.size(); i++) {
        final Test test = (Test)result.elementAt(i);
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

    Class testClass = loadTestClass(runner, suiteClassName);
    if (testClass == null) return null;
    Test test = null;
    if (methodName == null) {
      if (test == null) {
        try {
          Method suiteMethod = testClass.getMethod(BaseTestRunner.SUITE_METHODNAME, new Class[0]);
          if (!Modifier.isStatic(suiteMethod.getModifiers())) {
            String message = MessageFormat.format(ourBundle.getString("junit.suite.must.be.static"), new Object[]{testClass.getName()});
            System.err.println(message);
            //runFailed(message);
            return null;
          }
          try {
            //noinspection SSBasedInspection
            test = (Test)suiteMethod.invoke(null, new Class[0]); // static method 
            if (test == null) {
              return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, 
                                        MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), new Object[]{"method " + suiteClassName + ".suite() evaluates to null"}), 
                                        null);
            }
            test = new SuiteMethodWrapper(test, suiteClassName);
          }
          catch (final InvocationTargetException e) {
            final String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), new Object[]{testClass + " " + e.getTargetException().toString()});
            //System.err.println(message);
            //runner.runFailed(message);
            runner.clearStatus();
            return new FailedTestCase(testClass, BaseTestRunner.SUITE_METHODNAME, message, e);
          }
          catch (IllegalAccessException e) {
            String message = MessageFormat.format(ourBundle.getString("junit.failed.to.invoke.suite"), new Object[]{testClass + " " + e.toString()});
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

  private static Class loadTestClass(JUnit3IdeaTestRunner runner, String suiteClassName) {
    try {
      return Class.forName(suiteClassName, false, TestRunnerUtil.class.getClassLoader());
    }
    catch (ClassNotFoundException e) {
      String clazz = e.getMessage();
      if (clazz == null) {
        clazz = suiteClassName;
      }
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.found"), new Object[] {clazz}));
    }
    catch (Exception e) {
      runner.runFailed(MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e.toString()}));
    }
    return null;
  }

  private static Test createMethodSuite(JUnit3IdeaTestRunner runner, Class testClass, String methodName) {
    runner.clearStatus();
    try {
      Constructor constructor = testClass.getConstructor(new Class[]{String.class});
      return (Test)constructor.newInstance(new Object[]{methodName});
    }
    catch (NoSuchMethodException e) {
      try {
        Constructor constructor = testClass.getConstructor(new Class[0]);
        TestCase test = (TestCase)constructor.newInstance(new Object[0]);
        test.setName(methodName);
        return test;
      }
      catch(ClassCastException e1) {
        boolean methodExists;
        try {
          //noinspection SSBasedInspection
          testClass.getMethod(methodName, new Class[0]);
          methodExists = true;
        }
        catch (NoSuchMethodException e2) {
          methodExists = false;
        }
        if (!methodExists) {
          String error = MessageFormat.format(ourBundle.getString("junit.method.not.found"), new Object[]{methodName});
          String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{error});
          return new FailedTestCase(testClass, methodName, message, null);
        }
        runner.runFailed(MessageFormat.format(ourBundle.getString("junit.class.not.derived"), new Object[]{testClass.getName()}));
        return null;
      }
      catch (Exception e1) {
        String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e1.toString()});
        return new FailedTestCase(testClass, methodName, message, e1);
      }
    }
    catch (Throwable e) {
      String message = MessageFormat.format(ourBundle.getString("junit.cannot.instantiate.tests"), new Object[]{e.toString()});
      return new FailedTestCase(testClass, methodName, message, e);
    }
  }

  public static String testsFoundInPackageMesage(int testCount, String name) {
    return MessageFormat.format(ourBundle.getString("tests.found.in.package"), new Object[]{new Integer(testCount), name});
  }

  /** @noinspection JUnitTestClassNamingConvention, JUnitTestCaseWithNonTrivialConstructors, JUnitTestCaseWithNoTests */
  public static class FailedTestCase extends TestCase {
    private final String myMethodName;
    private final String myMessage;
    private final Throwable myThrowable;

    public FailedTestCase(final Class testClass, final String methodName, final String message, final Throwable e) {
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

    protected void runTest() throws Throwable {
      try {
        //noinspection Since15
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

    public int countTestCases() {
      return mySuite.countTestCases();
    }

    public void run(TestResult result) {
      mySuite.run(result);
    }

    public Test getSuite() {
      return mySuite;
    }
  }
}
