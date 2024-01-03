// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.engine.config.DefaultJupiterConfiguration;
import org.junit.jupiter.engine.descriptor.ClassTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.CompositeTestSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.FilePosition;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class JUnit5EventsTest {

  public static final ConfigurationParameters EMPTY_PARAMETER = new ConfigurationParameters() {
    @Override
    public Optional<String> get(String key) {
      return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
      return Optional.empty();
    }

    @Override
    public <T> Optional<T> get(String key, Function<String, T> transformer) {
      return ConfigurationParameters.super.get(key, transformer);
    }

    @Override
    public int size() {
      return 0;
    }

    @Override
    public Set<String> keySet() {
      return null;
    }
  };
  private JUnit5TestExecutionListener myExecutionListener;
  private StringBuffer myBuf;

  @BeforeEach
  void setUp() {
    myBuf = new StringBuffer();
    myExecutionListener = new JUnit5TestExecutionListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) {
        myBuf.append(new String(new byte[]{(byte)b}, StandardCharsets.UTF_8));
      }
    }, false, StandardCharsets.UTF_8)) {
      @Override
      protected long getDuration() {
        return 0;
      }

      @Override
      protected String getTrace(Throwable ex) {
        return "TRACE";
      }
    };
  }

  @AfterEach
  void tearDown() {
    myBuf = null;
  }

  @Test
  void multipleFailures() throws Exception {
    UniqueId engineId = UniqueId.forEngine("engine");
    EngineDescriptor engineDescriptor = new EngineDescriptor(engineId, "e");
    DefaultJupiterConfiguration jupiterConfiguration = createJupiterConfiguration();
    UniqueId classId = engineId.append("class", "testClass");
    ClassTestDescriptor c = new ClassTestDescriptor(classId, TestClass.class, jupiterConfiguration);
    engineDescriptor.addChild(c);
    TestDescriptor testDescriptor = new TestMethodTestDescriptor(classId.append("method", "testMethod"), TestClass.class,
                                                                 TestClass.class.getDeclaredMethod("test1"),
                                                                 jupiterConfiguration);
    c.addChild(testDescriptor);
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);
    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor), EMPTY_PARAMETER);
    //run from class
    myExecutionListener.setRootName("testClass");
    myExecutionListener.testPlanExecutionStarted(testPlan);
    myExecutionListener.setPresentableName("testClass");

    //engine
    myExecutionListener.executionStarted(TestIdentifier.from(engineDescriptor));
    //class
    myExecutionListener.executionStarted(TestIdentifier.from(c));

    myExecutionListener.executionStarted(identifier);
    MultipleFailuresError multipleFailuresError = new MultipleFailuresError("2 errors", Arrays.asList
      (new AssertionFailedError("message1", "expected1", "actual1"),
       new AssertionFailedError("message2", "expected2", "actual2")));
    Map<String, String> map = new TreeMap<>();
    map.put("key1", "value1");
    map.put("stdout", "out1");
    ReportEntry reportEntry = ReportEntry.from(map);
    myExecutionListener.reportingEntryPublished(identifier, reportEntry);
    myExecutionListener.executionFinished(identifier, TestExecutionResult.failed(multipleFailuresError));

    myExecutionListener.executionFinished(TestIdentifier.from(c), TestExecutionResult.successful());
    myExecutionListener.executionFinished(TestIdentifier.from(engineDescriptor), TestExecutionResult.successful());


    String lineSeparators = StringUtil.convertLineSeparators(myBuf.toString()).replaceAll("\\|r", "");
    Assertions.assertEquals("##teamcity[enteredTheMatrix]\n" +
                            "##teamcity[rootName name = 'testClass' location = 'java:suite://testClass']\n" +
                            "##teamcity[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass/test1' metainfo='']\n" +
                            "##teamcity[testStdOut id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' out = 'timestamp = " + reportEntry.getTimestamp() +", key1 = value1, stdout = out1|n']\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='message1|nComparison Failure: ' expected='expected1' actual='actual1' details='TRACE']\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='message2|nComparison Failure: ' expected='expected2' actual='actual2' details='TRACE']\n" +
                            "##teamcity[testFailed name='test1()' id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0' message='2 errors (2 failures)|n\torg.opentest4j.AssertionFailedError: message1|n\torg.opentest4j.AssertionFailedError: message2' details='TRACE']\n" +
                            "##teamcity[testFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='test1()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='0']\n",
                            lineSeparators);
  }

  @Test
  void testsWithExplicitTestSources() {
    class DummyTestDescriptor extends AbstractTestDescriptor {
      protected DummyTestDescriptor(UniqueId uniqueId, String displayName, TestSource source) {
        super(uniqueId, displayName, source);
      }

      @Override
      public Type getType() {
        return Type.TEST;
      }
    }

    UniqueId engineId = UniqueId.forEngine("engine");
    EngineDescriptor engineDescriptor = new EngineDescriptor(engineId, "e");
    DefaultJupiterConfiguration jupiterConfiguration = createJupiterConfiguration();
    UniqueId classId = engineId.append("class", "testClass");
    ClassTestDescriptor c = new ClassTestDescriptor(classId, TestClass.class, jupiterConfiguration);
    engineDescriptor.addChild(c);

    List<TestDescriptor> testDescriptors =
      List.of(new DummyTestDescriptor(classId.append("method", "test1"), "test1 display name",
                                      ClassSource.from(TestClass.class, FilePosition.from(111, 222))),
              new DummyTestDescriptor(classId.append("method", "test2"), "test2 display name",
                                      FileSource.from(new File("/directory/test2.java"),
                                                      FilePosition.from(12, 13))),
              new DummyTestDescriptor(classId.append("method", "test3"), "test3 display name",
                                      MethodSource.from("TestClass", "test4Method", "java.lang.String,java.util.List")),
              new DummyTestDescriptor(classId.append("method", "test4"), "test4 display name",
                                      CompositeTestSource.from(List.of(
                                        ClassSource.from(JUnit5EventsTest.class, FilePosition.from(123, 456)),
                                        ClassSource.from(TestClass.class, FilePosition.from(3, 4))
                                      ))));

    testDescriptors.forEach(c::addChild);
    myExecutionListener.testPlanExecutionStarted(TestPlan.from(Collections.singleton(engineDescriptor), EMPTY_PARAMETER));
    testDescriptors.stream().map(TestIdentifier::from).forEach(myExecutionListener::executionStarted);


    String lineSeparators = StringUtil.convertLineSeparators(myBuf.toString()).replaceAll("\\|r", "");
    Assertions.assertEquals(
      """
        ##teamcity[enteredTheMatrix]
        ##teamcity[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test1|]' name='test1 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test1|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass' metainfo='110:221']
        ##teamcity[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test2|]' name='test2 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test2|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='file:///directory/test2.java:12']
        ##teamcity[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test3|]' name='test3 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test3|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://TestClass/test4Method' metainfo='java.lang.String,java.util.List']
        ##teamcity[testStarted id='|[engine:engine|]/|[class:testClass|]/|[method:test4|]' name='test4 display name' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:test4|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest']
                """,
      lineSeparators);
  }

  public static DefaultJupiterConfiguration createJupiterConfiguration() {
    return new DefaultJupiterConfiguration(EMPTY_PARAMETER);
  }

  @Test
  void containerFailure() throws Exception {
    UniqueId engineId = UniqueId.forEngine("engine");
    EngineDescriptor engineDescriptor = new EngineDescriptor(engineId, "e");
    DefaultJupiterConfiguration jupiterConfiguration = createJupiterConfiguration();
    UniqueId classId = engineId.append("class", "testClass");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(classId, TestClass.class,
                                                                      jupiterConfiguration);
    engineDescriptor.addChild(classTestDescriptor);
    TestDescriptor testDescriptor = new TestFactoryTestDescriptor(classId.append("method", "testMethod"), TestClass.class,
                                                                  TestClass.class.getDeclaredMethod("brokenStream"),
                                                                  jupiterConfiguration);
    classTestDescriptor.addChild(testDescriptor);
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);

    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor), EMPTY_PARAMETER);
    myExecutionListener.setRootName("testMethod");
    myExecutionListener.setSendTree();
    myExecutionListener.testPlanExecutionStarted(testPlan);
    myExecutionListener.setPresentableName("testMethod");
    myExecutionListener.executionStarted(identifier);
    myExecutionListener.executionFinished(identifier, TestExecutionResult.failed(new IllegalStateException()));

    Assertions.assertEquals("""
          ##teamcity[enteredTheMatrix]
          ##teamcity[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']
          ##teamcity[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0']
          ##teamcity[treeEnded]
          ##teamcity[rootName name = 'testMethod' location = 'java:suite://testMethod']
          ##teamcity[testStarted  name='Class Configuration' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]'  ]
          ##teamcity[testFailed name='Class Configuration' id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' error='true' message='' details='TRACE']
          ##teamcity[testFinished name='Class Configuration' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' ]
          ##teamcity[testSuiteFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
          """, StringUtil.convertLineSeparators(myBuf.toString()));
  }

  @Test
  void nestedEngines() {
    UniqueId engineId = UniqueId.forEngine("engine");
    EngineDescriptor engineDescriptor = new EngineDescriptor(engineId, "e");
    DefaultJupiterConfiguration jupiterConfiguration = createJupiterConfiguration();
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(engineId.append("suite", "suiteClass"), TestClass.class,
                                                                      jupiterConfiguration);
    engineDescriptor.addChild(classTestDescriptor);

    UniqueId nestedEngineId = UniqueId.forEngine("secondEngine");
    EngineDescriptor nestedEngine = new EngineDescriptor(nestedEngineId, "nested engine");
    classTestDescriptor.addChild(nestedEngine);

    ClassTestDescriptor testDescriptor = new ClassTestDescriptor(nestedEngineId.append("class", "testClass"), TestClass.class, jupiterConfiguration);
    nestedEngine.addChild(testDescriptor);

    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor), EMPTY_PARAMETER);
    myExecutionListener.setSendTree();
    myExecutionListener.testPlanExecutionStarted(testPlan);
    TestIdentifier testIdentifier = TestIdentifier.from(testDescriptor);
    myExecutionListener.executionStarted(testIdentifier);
    myExecutionListener.executionFinished(testIdentifier, TestExecutionResult.successful());

    Assertions.assertEquals("""
                              ##teamcity[enteredTheMatrix]
                              ##teamcity[suiteTreeStarted id='|[engine:engine|]/|[suite:suiteClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[suite:suiteClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']
                              ##teamcity[suiteTreeStarted id='|[engine:secondEngine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']
                              ##teamcity[suiteTreeEnded id='|[engine:secondEngine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]']
                              ##teamcity[suiteTreeEnded id='|[engine:engine|]/|[suite:suiteClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[suite:suiteClass|]' parentNodeId='0']
                              ##teamcity[treeEnded]
                              ##teamcity[testSuiteStarted id='|[engine:secondEngine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]'locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']
                              ##teamcity[testSuiteFinished id='|[engine:secondEngine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:secondEngine|]/|[class:testClass|]' parentNodeId='|[engine:engine|]/|[suite:suiteClass|]']
                              """, StringUtil.convertLineSeparators(myBuf.toString()));
  }

  @Test
  void containerDisabled() throws Exception {
    UniqueId engineId = UniqueId.forEngine("engine");
    EngineDescriptor engineDescriptor = new EngineDescriptor(engineId, "e");
    DefaultJupiterConfiguration jupiterConfiguration = createJupiterConfiguration();
    UniqueId classId = engineId.append("class", "testClass");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(classId, TestClass.class,
                                                                      jupiterConfiguration);
    engineDescriptor.addChild(classTestDescriptor);
    TestDescriptor testDescriptor = new TestFactoryTestDescriptor(classId.append("method", "testMethod"), TestClass.class,
                                                                  TestClass.class.getDeclaredMethod("brokenStream"),
                                                                  jupiterConfiguration);
    classTestDescriptor.addChild(testDescriptor);
    TestIdentifier identifier = TestIdentifier.from(testDescriptor);

    final TestPlan testPlan = TestPlan.from(Collections.singleton(engineDescriptor), EMPTY_PARAMETER);
    myExecutionListener.setSendTree();
    myExecutionListener.testPlanExecutionStarted(testPlan);
    myExecutionListener.executionStarted(identifier);
    myExecutionListener.executionFinished(identifier, TestExecutionResult.aborted(null));

    Assertions.assertEquals("""
                              ##teamcity[enteredTheMatrix]
                              ##teamcity[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0' locationHint='java:suite://com.intellij.junit5.JUnit5EventsTest$TestClass']
                              ##teamcity[suiteTreeStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]' locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass/brokenStream' metainfo='']
                              ##teamcity[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              ##teamcity[suiteTreeEnded id='|[engine:engine|]/|[class:testClass|]' name='JUnit5EventsTest$TestClass' nodeId='|[engine:engine|]/|[class:testClass|]' parentNodeId='0']
                              ##teamcity[treeEnded]
                              ##teamcity[testSuiteStarted id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]'locationHint='java:test://com.intellij.junit5.JUnit5EventsTest$TestClass/brokenStream' metainfo='']
                              ##teamcity[testIgnored name='brokenStream()' id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              ##teamcity[testSuiteFinished id='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' name='brokenStream()' nodeId='|[engine:engine|]/|[class:testClass|]/|[method:testMethod|]' parentNodeId='|[engine:engine|]/|[class:testClass|]']
                              """,
                            StringUtil.convertLineSeparators(myBuf.toString()));
  }

  // This class is actually the test-data
  @SuppressWarnings("JUnitMalformedDeclaration")
  private static class TestClass {
    @Test
    void test1() {
    }

    @TestFactory
    Stream<DynamicTest> brokenStream() {
      return Stream.generate(() -> { throw new IllegalStateException(); });
    }
  }
}
