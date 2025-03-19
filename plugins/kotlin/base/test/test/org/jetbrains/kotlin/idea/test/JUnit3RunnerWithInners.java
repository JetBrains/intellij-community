// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.util.ArrayUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.test.TestIndexingMode;
import org.junit.Ignore;
import org.junit.internal.MethodSorter;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.*;
import org.junit.runner.notification.RunNotifier;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class JUnit3RunnerWithInners extends Runner implements Filterable, Sortable {
    private static final Set<Class<?>> requestedRunners = new HashSet<>();

    private JUnit38ClassRunner delegateRunner;
    private final Class<?> testClass;
    private boolean isFakeTest = false;

    public JUnit3RunnerWithInners(Class<?> testClass) {
        this.testClass = testClass;
        requestedRunners.add(testClass);
    }

    @Override
    public void run(RunNotifier notifier) {
        initialize();
        delegateRunner.run(notifier);
    }

    @Override
    public Description getDescription() {
        initialize();
        return isFakeTest ? Description.EMPTY : delegateRunner.getDescription();
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        initialize();
        delegateRunner.filter(filter);
    }

    @Override
    public void sort(Sorter sorter) {
        initialize();
        delegateRunner.sort(sorter);
    }

    protected void initialize() {
        if (delegateRunner != null) return;
        delegateRunner = new JUnit38AssumeSupportRunner(getCollectedTests());
    }

    private Test getCollectedTests() {
        List<Class<?>> innerClasses = collectDeclaredClasses(testClass, false);
        Set<Class<?>> unprocessedInnerClasses = unprocessedClasses(innerClasses);

        if (unprocessedInnerClasses.isEmpty()) {
            if (!innerClasses.isEmpty() && !hasTestMethods(testClass)) {
                isFakeTest = true;
                return new JUnit3RunnerWithInners.FakeEmptyClassTest(testClass);
            } else {
                return new TestSuite(testClass.asSubclass(TestCase.class));
            }
        } else if (unprocessedInnerClasses.size() == innerClasses.size()) {
            return createTreeTestSuite(testClass);
        } else {
            return new TestSuite(testClass.asSubclass(TestCase.class));
        }
    }

    private static Test createTreeTestSuite(Class<?> root) {
        Set<Class<?>> classes = new LinkedHashSet<>(collectDeclaredClasses(root, true));
        Map<Class<?>, TestSuite> classSuites = new HashMap<>();

        for (Class<?> aClass : classes) {
            TestSuite testSuite = hasTestMethods(aClass) ? new TestSuite(aClass) : new TestSuite(aClass.getCanonicalName());
            processIndexingMode(aClass, testSuite);
            classSuites.put(aClass, testSuite);
        }

        for (Class<?> aClass : classes) {
            if (aClass.getEnclosingClass() != null && classes.contains(aClass.getEnclosingClass())) {
                classSuites.get(aClass.getEnclosingClass()).addTest(classSuites.get(aClass));
            }
        }

        return classSuites.get(root);
    }

    private static void processIndexingMode(Class<?> aClass, TestSuite testSuite) {
        if (!TestIndexingModeSupporter.class.isAssignableFrom(aClass)) return;
        //noinspection unchecked
        Class<? extends TestIndexingModeSupporter> clazz = (Class<? extends TestIndexingModeSupporter>) aClass;

        TestIndexingModeSupporter.IndexingMode[] modes = getIndexingModes(aClass);
        if (modes != null) {
            for (TestIndexingModeSupporter.IndexingMode mode : modes) {
                TestIndexingModeSupporter.IndexingModeTestHandler handler = null;
                switch (mode) {
                    case DUMB_EMPTY_INDEX -> handler = new TestIndexingModeSupporter.EmptyIndexSuite();
                    case DUMB_RUNTIME_ONLY_INDEX -> handler = new TestIndexingModeSupporter.RuntimeOnlyIndexSuite();
                    case DUMB_FULL_INDEX -> handler = new TestIndexingModeSupporter.FullIndexSuite();
                }
                if (handler != null) {
                    TestIndexingModeSupporter.addTest(clazz, handler, testSuite);
                }
            }
        }
    }

    private static Set<Class<?>> unprocessedClasses(Collection<Class<?>> classes) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> aClass : classes) {
            if (!requestedRunners.contains(aClass)) {
                result.add(aClass);
            }
        }

        return result;
    }

    private static List<Class<?>> collectDeclaredClasses(Class<?> klass, boolean withItself) {
        List<Class<?>> result = new ArrayList<>();
        if (withItself) {
            result.add(klass);
        }

        for (Class<?> aClass : klass.getDeclaredClasses()) {
            result.addAll(collectDeclaredClasses(aClass, true));
        }

        return result;
    }

    private static boolean hasTestMethods(Class<?> klass) {
        for (Class<?> currentClass = klass; Test.class.isAssignableFrom(currentClass); currentClass = currentClass.getSuperclass()) {
            for (Method each : MethodSorter.getDeclaredMethods(currentClass)) {
                if (isTestMethod(each)) return true;
            }
        }

        return false;
    }

    private static TestIndexingModeSupporter.IndexingMode[] getIndexingModes(Class<?> klass) {
        for (Class<?> currentClass = klass; Test.class.isAssignableFrom(currentClass); currentClass = currentClass.getSuperclass()) {
            TestIndexingMode indexingMode = currentClass.getAnnotation(TestIndexingMode.class);
            if (indexingMode != null) {
                TestIndexingModeSupporter.@NotNull IndexingMode[] value = indexingMode.value();
                if (!Registry.is("ide.dumb.mode.check.awareness")) {
                    return ArrayUtil.remove(value, TestIndexingModeSupporter.IndexingMode.DUMB_EMPTY_INDEX);
                }
                return value;
            }
        }
        return null;
    }

    static class FakeEmptyClassTest implements Test, Filterable {
        private final String className;

        FakeEmptyClassTest(Class<?> klass) {
            this.className = klass.getName();
        }

        @Override
        public int countTestCases() {
            return 0;
        }

        @Override
        public void run(TestResult result) {
            result.startTest(this);
            result.endTest(this);
        }

        @Override
        public String toString() {
            return "Empty class with inners for " + className;
        }

        @Override
        public void filter(Filter filter) throws NoTestsRemainException {
            throw new NoTestsRemainException();
        }
    }

    static boolean isTestMethod(Method method) {
        return method.getParameterTypes().length == 0 &&
               method.getName().startsWith("test") &&
               method.getReturnType().equals(Void.TYPE) &&
               Modifier.isPublic(method.getModifiers()) &&
               method.getAnnotation(Ignore.class) == null;
    }
}