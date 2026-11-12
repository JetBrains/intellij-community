// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tests.bazel;

import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.TestIdentifier;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCityTestNamesTest {
  @Test
  void aClassThatCannotBeLoadedIsNoSuite() {
    assertThat(TeamCityTestNames.isSuite(classContainer(ClassSource.from("com.example.NoSuchClass")))).isFalse();
  }

  @Test
  void aJUnit3SuiteIsASuite() {
    assertThat(TeamCityTestNames.isSuite(classContainer(ClassSource.from(junit.framework.TestSuite.class)))).isTrue();
    assertThat(TeamCityTestNames.isSuite(classContainer(ClassSource.from(TestCase.class)))).isFalse();
  }

  private static TestIdentifier classContainer(ClassSource source) {
    return TestIdentifier.from(new AbstractTestDescriptor(UniqueId.forEngine("engine").append("class", source.getClassName()),
                                                          source.getClassName(), source) {
      @Override
      public Type getType() {
        return Type.CONTAINER;
      }
    });
  }
}
