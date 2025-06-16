// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.openapi.util.text.StringUtil;
import org.junit.jupiter.engine.config.DefaultJupiterConfiguration;
import org.junit.jupiter.engine.descriptor.ClassTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor;
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor;
import org.junit.platform.engine.*;
import org.junit.platform.engine.reporting.OutputDirectoryProvider;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("UnusedReturnValue")
public class JUnit5TestRunnerBuilder {
  private static final ConfigurationParameters EMPTY_PARAMETER = new ConfigurationParameters() {
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

  private static final OutputDirectoryProvider EMPTY_OUTPUT_DIRECTORY_PROVIDER = new OutputDirectoryProvider() {
    @Override
    public Path getRootDirectory() {
      return null;
    }

    @Override
    public Path createOutputDirectory(TestDescriptor testDescriptor) {
      return null;
    }
  };

  private final StringBuffer myStringBuffer = new StringBuffer();
  private final JUnit5TestExecutionListener myExecutionListener;
  private final UniqueId myEngineId;
  private final EngineDescriptor myEngineDescriptor;
  private final DefaultJupiterConfiguration myJupiterConfiguration;
  private TestPlan myTestPlan;
  private String myRootName;
  private boolean mySendTree = false;
  private String myPresentableName;

  private TestDescriptor nestedClassDescriptor;

  public JUnit5TestRunnerBuilder() {
    myEngineId = UniqueId.forEngine("engine");
    myEngineDescriptor = new EngineDescriptor(myEngineId, "e");
    myJupiterConfiguration = createJupiterConfiguration();
    myExecutionListener = new JUnit5TestExecutionListener(new PrintStream(new OutputStream() {
      @Override
      public void write(int b) {
        myStringBuffer.append(new String(new byte[]{(byte)b}, StandardCharsets.UTF_8));
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

  public TestDescriptorContext withTestMethod(Class<?> testClass, String methodName) throws NoSuchMethodException {
    UniqueId classId = myEngineId.append("class", "testClass");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(classId, testClass, myJupiterConfiguration);
    myEngineDescriptor.addChild(classTestDescriptor);

    Method method = testClass.getDeclaredMethod(methodName);
    UniqueId methodId = classId.append("method", "testMethod");
    TestDescriptor testDescriptor = new TestMethodTestDescriptor(methodId, testClass, method, () -> List.of(), myJupiterConfiguration);
    classTestDescriptor.addChild(testDescriptor);

    return new TestDescriptorContext(testDescriptor, classTestDescriptor);
  }

  public JUnit5TestRunnerBuilder withRootName(String rootName) {
    this.myRootName = rootName;
    return this;
  }

  public JUnit5TestRunnerBuilder withPresentableName(String presentableName) {
    this.myPresentableName = presentableName;
    return this;
  }

  public JUnit5TestRunnerBuilder withSendTree() {
    this.mySendTree = true;
    return this;
  }

  public JUnit5TestRunnerBuilder withNestedEngine(String engineName, Class<?> testClass) {
    // create suite
    UniqueId suiteId = myEngineId.append("suite", "suiteClass");
    ClassTestDescriptor suiteTestDescriptor = new ClassTestDescriptor(suiteId, testClass, myJupiterConfiguration);
    myEngineDescriptor.addChild(suiteTestDescriptor);

    // add nested engine into suite
    UniqueId nestedEngineId = UniqueId.forEngine(engineName);
    EngineDescriptor nestedEngine = new EngineDescriptor(nestedEngineId, "nested engine");
    suiteTestDescriptor.addChild(nestedEngine);

    // add class descriptor into "nested engine"
    ClassTestDescriptor nestedTestDescriptor = new ClassTestDescriptor(
      nestedEngineId.append("class", "testClass"),
      testClass,
      myJupiterConfiguration
    );
    nestedEngine.addChild(nestedTestDescriptor);

    // save the nested engine descriptor for later use
    this.nestedClassDescriptor = nestedTestDescriptor;

    return this;
  }

  public TestDescriptor getNestedClassDescriptor() {
    return nestedClassDescriptor;
  }

  public TestDescriptorContext withTestDescriptor(Class<?> testClass, String methodName, String displayName, TestSource source) {
    UniqueId classId = myEngineId.append("class", "testClass");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(classId, testClass, myJupiterConfiguration);
    myEngineDescriptor.addChild(classTestDescriptor);

    UniqueId methodId = classId.append("method", methodName);
    TestDescriptor testDescriptor = new AbstractTestDescriptor(methodId, displayName, source) {
      @Override
      public Type getType() {
        return Type.TEST;
      }
    };
    classTestDescriptor.addChild(testDescriptor);

    return new TestDescriptorContext(testDescriptor, classTestDescriptor);
  }

  public TestDescriptorContext withTestFactory(Class<?> testClass, String methodName) throws NoSuchMethodException {
    UniqueId classId = myEngineId.append("class", "testClass");
    ClassTestDescriptor classTestDescriptor = new ClassTestDescriptor(classId, testClass, myJupiterConfiguration);
    myEngineDescriptor.addChild(classTestDescriptor);

    Method method = testClass.getDeclaredMethod(methodName);
    UniqueId methodId = classId.append("method", "testMethod");
    TestDescriptor testDescriptor = new TestFactoryTestDescriptor(methodId, testClass, method, () -> List.of(), myJupiterConfiguration);
    classTestDescriptor.addChild(testDescriptor);

    return new TestDescriptorContext(testDescriptor, classTestDescriptor);
  }

  public JUnit5TestRunnerBuilder buildTestPlan() {
    myTestPlan = TestPlan.from(Collections.singleton(myEngineDescriptor), EMPTY_PARAMETER, EMPTY_OUTPUT_DIRECTORY_PROVIDER);
    return this;
  }

  public static DefaultJupiterConfiguration createJupiterConfiguration() {
    return new DefaultJupiterConfiguration(EMPTY_PARAMETER, EMPTY_OUTPUT_DIRECTORY_PROVIDER);
  }

  /**
   * Executes the test plan.
   * This method should be called after buildTestPlan().
   *
   * @return this builder
   */
  public JUnit5TestRunnerBuilder execute() {
    if (myTestPlan == null) {
      buildTestPlan();
    }
    if (myRootName != null) {
      myExecutionListener.setRootName(myRootName);
    }
    if (mySendTree) {
      myExecutionListener.setSendTree();
    }
    myExecutionListener.testPlanExecutionStarted(myTestPlan);
    if (myPresentableName != null) {
      myExecutionListener.setPresentableName(myPresentableName);
    }
    return this;
  }

  public String getFormattedOutput() {
    return StringUtil.convertLineSeparators(myStringBuffer.toString()).replaceAll("\\|r", "");
  }

  public JUnit5TestExecutionListener getExecutionListener() {
    return myExecutionListener;
  }

  public class TestDescriptorContext {
    private final TestDescriptor myParentDescriptor;
    private final TestIdentifier myIdentifier;

    private boolean myEngineStarted = false;
    private boolean myParentStarted = false;
    private boolean myTestStarted = false;

    public TestDescriptorContext(TestDescriptor testDescriptor, TestDescriptor parentDescriptor) {
      this.myParentDescriptor = parentDescriptor;
      this.myIdentifier = TestIdentifier.from(testDescriptor);
    }

    public TestDescriptorContext startExecution() {
      if (!myEngineStarted) {
        myExecutionListener.executionStarted(TestIdentifier.from(myEngineDescriptor));
        myEngineStarted = true;
      }
      if (myParentDescriptor != myEngineDescriptor && !myParentStarted) {
        myExecutionListener.executionStarted(TestIdentifier.from(myParentDescriptor));
        myParentStarted = true;
      }
      if (!myTestStarted) {
        myExecutionListener.executionStarted(myIdentifier);
        myTestStarted = true;
      }
      return this;
    }

    public TestDescriptorContext startTestOnly() {
      if (!myTestStarted) {
        myExecutionListener.executionStarted(myIdentifier);
        myTestStarted = true;
      }
      return this;
    }

    public JUnit5TestRunnerBuilder finishWithFailure(Throwable throwable) {
      if (myTestStarted) {
        myExecutionListener.executionFinished(myIdentifier, TestExecutionResult.failed(throwable));
      }
      if (myParentStarted) {
        myExecutionListener.executionFinished(TestIdentifier.from(myParentDescriptor), TestExecutionResult.successful());
      }
      if (myEngineStarted) {
        myExecutionListener.executionFinished(TestIdentifier.from(myEngineDescriptor), TestExecutionResult.successful());
      }
      return JUnit5TestRunnerBuilder.this;
    }

    public JUnit5TestRunnerBuilder finishAborted() {
      if (myTestStarted) {
        myExecutionListener.executionFinished(myIdentifier, TestExecutionResult.aborted(null));
      }
      return JUnit5TestRunnerBuilder.this;
    }

    public JUnit5TestRunnerBuilder finish() {
      if (myTestStarted) {
        myExecutionListener.executionFinished(myIdentifier, TestExecutionResult.successful());
      }
      return JUnit5TestRunnerBuilder.this;
    }

    public TestDescriptorContext publishReportEntry(ReportEntry reportEntry) {
      myExecutionListener.reportingEntryPublished(myIdentifier, reportEntry);
      return this;
    }
  }
}
