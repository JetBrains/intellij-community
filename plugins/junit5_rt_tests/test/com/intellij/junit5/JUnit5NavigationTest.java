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
package com.intellij.junit5;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.*;
import org.junit.platform.launcher.TestIdentifier;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import static java.util.Collections.singletonList;

@DisplayName("junit 5 navigation features: location strings, etc")
class JUnit5NavigationTest {

  @Test
  void methodNavigation() throws Exception {
    UniqueId uniqueId = UniqueId.parse("[class:JUnit5NavigationTest]/[method:methodNavigation]");
    TestMethodTestDescriptor methodTestDescriptor =
      new TestMethodTestDescriptor(uniqueId, JUnit5NavigationTest.class, JUnit5NavigationTest.class.getDeclaredMethod("methodNavigation"));
    TestIdentifier testIdentifier = TestIdentifier.from(methodTestDescriptor);
    Assertions.assertEquals(JUnit5NavigationTest.class.getName(), JUnit5TestExecutionListener.getClassName(testIdentifier));
    Assertions.assertEquals("methodNavigation", JUnit5TestExecutionListener.getMethodName(testIdentifier));
    //Assertions.assertEquals("methodNavigation", testIdentifier.getDisplayName()); todo methodNavigation()
  }

  private TestSource myTestSource = null;

  @Test
  void ignoreMissingTestSource() {
    myTestSource = null;

    Assertions.assertEquals("", locationHint());
  }

  @Test
  void ignoreUnsupportedTestSources() {
    myTestSource = anyUnsupportedTestSource();

    Assertions.assertEquals("", locationHint());
  }

  @Test
  void produceLocationHintForAnySupportedTestSource() {
    myTestSource = anySupportedSource();
    String locationHint = locationHint();

    Assertions.assertTrue(locationHint.startsWith("locationHint='"), locationHint);
    Assertions.assertTrue(locationHint.endsWith("'"), locationHint);
  }

  @Test
  void locationHintValueForMethodSource() {
    myTestSource = MethodSource.from("className", "methodName");
    String locationHintValue = locationHintValue();

    Assertions.assertTrue(locationHintValue.startsWith("java:"), locationHintValue);
    Assertions.assertTrue(locationHintValue.contains("://className.methodName"), locationHintValue);
  }

  @Test
  void locationHintValueForClassSource() {
    myTestSource = ClassSource.from(String.class);
    String locationHintValue = locationHintValue();

    Assertions.assertTrue(locationHintValue.startsWith("java:"), locationHintValue);
    Assertions.assertTrue(locationHintValue.contains("://java.lang.String"), locationHintValue);
  }

  @Test
  void deriveSuiteOrTestFromDescription() {
    myTestSource = methodOrClassSource();
    Assertions.assertTrue(locationHintValue().startsWith("java:suite:"));
  }

  @Test
  void locationHintValueForFileSource() throws IOException {
    myTestSource = FileSource.from(Paths.get("/some/file.txt").toFile());

    Assertions.assertEquals("file://" + ((FileSource)myTestSource).getFile().getCanonicalPath(), locationHintValue());
  }


  @Test
  void locationHintValueForFileSourceWithLineInformation() throws IOException {
    myTestSource = FileSource.from(Paths.get("/some/file.txt").toFile(), FilePosition.from(22, 7));

    Assertions.assertEquals("file://" + ((FileSource)myTestSource).getFile().getCanonicalPath() +":22", locationHintValue());
  }

  @Test
  void ignoreCompositeSourceWithoutAnySupportedTestSource() {
    myTestSource = CompositeTestSource.from(singletonList(anyUnsupportedTestSource()));

    Assertions.assertEquals("", locationHint());
  }

  @Test
  void locationHintValueForCompositeSourcePickTheFirstSupportedSourceInTheList() {
    myTestSource = CompositeTestSource.from(Arrays.asList(anyUnsupportedTestSource(), ClassSource.from(String.class),
                                                          MethodSource.from("className", "methodName")));
    String locationHintValue = locationHintValue();

    Assertions.assertTrue(locationHintValue.startsWith("java:"), locationHintValue);
    Assertions.assertTrue(locationHintValue.contains("://java.lang.String"), locationHintValue);
  }

  private String locationHint() {
    TestIdentifier testIdentifier = TestIdentifier.from(new ConfigurableTestDescriptor(myTestSource));
    return JUnit5TestExecutionListener.getLocationHint(testIdentifier);
  }

  private String locationHintValue() {
    return locationHintValue(new ConfigurableTestDescriptor(myTestSource));
  }

  private static String locationHintValue(final ConfigurableTestDescriptor descriptor) {
    TestIdentifier testIdentifier = TestIdentifier.from(descriptor);
    return JUnit5TestExecutionListener.getLocationHintValue(testIdentifier.getSource().orElseThrow(IllegalStateException::new));
  }

  private static ClassSource anySupportedSource() {
    return ClassSource.from(String.class);
  }

  private static TestSource methodOrClassSource() {
    return ClassSource.from(String.class);
  }

  private static TestSource anyUnsupportedTestSource() {
    return new TestSource() {
    };
  }

  private static UniqueId anyUniqueId() {
    return UniqueId.forEngine("stand in");
  }

  private static String anyDisplayName() {
    return "stand in";
  }

  private static class ConfigurableTestDescriptor extends AbstractTestDescriptor {
    private boolean myIsTest;

    public ConfigurableTestDescriptor(TestSource testSource) {
      super(JUnit5NavigationTest.anyUniqueId(), JUnit5NavigationTest.anyDisplayName(), testSource);
    }


    @Override
    public Type getType() {
      return myIsTest ? Type.TEST : Type.CONTAINER;
    }

    public void isTest(boolean isTest) {
      myIsTest = isTest;
    }
  }
}
