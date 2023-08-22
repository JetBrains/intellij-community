// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.GroupBasedTestClassFilter;
import com.intellij.PatternListTestClassFilter;
import com.intellij.TestClassesFilter;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.Test;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import static com.intellij.GroupBasedTestClassFilter.createOn;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestClassesFilterTest {
  private static final String FILTER_TEXT = """
    [Group1]
    com.intellij.package1.*
    com.intellij.package2.ExcludedTest
    com.intellij.package3.*package4

    [Group2]
    com.intellij.package5.*
    com.intellij.package6.ExcludedTest
    com.intellij.package7.*package8
    [Group3]
    org.jetbrains.*
    -org.jetbrains.excluded.*
    [Group4]
    org.jetbrains.excluded.TestIncludeInG4""";

  @Test
  public void excluded() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of("Group3"));
    assertTrue(classesFilter.matches("org.jetbrains.included"));
    assertTrue(classesFilter.matches("org.jetbrains.included.Test1"));
    assertFalse(classesFilter.matches("org.jetbrains.excluded.Test1"));
    assertFalse(classesFilter.matches("org.jetbrains.excluded.sub.Test1"));
    assertTrue(classesFilter.matches("org.jetbrains.excluded"));
  }

  @Test
  public void excludedFromGroup3ShouldBeInAllExcluded() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED));
    assertTrue(classesFilter.matches("org.jetbrains.excluded.Test1"));
  }

  @Test
  public void excludedFromGroup3AndIncludeInGroup4ShouldBeOutAllExcluded() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED));
    assertFalse(classesFilter.matches("org.jetbrains.excluded.TestIncludeInG4"));
  }

  @Test
  public void group1AndAllExcludeDefined() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of("Group1", GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED));
    assertTrue(classesFilter.matches("com.intellij.package1.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test"));
    assertTrue(classesFilter.matches("com.intellij.package4.Test"));
  }

  @Test
  public void group1AndGroup2() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of("Group1", "Group2"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test"));
    assertTrue(classesFilter.matches("com.intellij.package5.Test"));
    assertFalse(classesFilter.matches("com.intellij.package4.Test"));
  }

  @Test
  public void emptyList() throws Exception {
    checkForAllExcludedDefinedGroup(createOn(new StringReader(FILTER_TEXT), Collections.emptyList()));
  }

  @Test
  public void allExcluded() throws Exception {
    checkForAllExcludedDefinedGroup(createOn(new StringReader(FILTER_TEXT), List.of(GroupBasedTestClassFilter.ALL_EXCLUDE_DEFINED)));
  }

  @Test
  public void justClassName() {
    PatternListTestClassFilter classesFilter =
      new PatternListTestClassFilter(StringUtil.split("com.intellij.database.DatabaseParametrizedTestSuite", ";"));
    assertTrue(classesFilter.matches("com.intellij.database.DatabaseParametrizedTestSuite", null));
  }

  @Test
  public void group2() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of("Group2"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package2.Test"));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package3.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3"));
    assertFalse(classesFilter.matches("com.intellij"));
    assertFalse(classesFilter.matches("com.intellij.Test"));
    assertTrue(classesFilter.matches("com.intellij.package5.Test"));
    assertTrue(classesFilter.matches("com.intellij.package5.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package6.Test"));
    assertTrue(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertTrue(classesFilter.matches("com.intellij.package7.package8"));
    assertTrue(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7"));
  }

  @Test
  public void group1() throws Exception {
    TestClassesFilter classesFilter = createOn(new StringReader(FILTER_TEXT), List.of("Group1"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test"));
    assertTrue(classesFilter.matches("com.intellij.package1.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package2.Test"));
    assertTrue(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertTrue(classesFilter.matches("com.intellij.package3.package4"));
    assertTrue(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3"));
    assertFalse(classesFilter.matches("com.intellij"));
    assertFalse(classesFilter.matches("com.intellij.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2"));
    assertFalse(classesFilter.matches("com.intellij.package6.Test"));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package7.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7"));
  }

  private static void checkForAllExcludedDefinedGroup(TestClassesFilter classesFilter) {
    assertFalse(classesFilter.matches("com.intellij.package1.Test"));
    assertFalse(classesFilter.matches("com.intellij.package1.Test2"));
    assertTrue(classesFilter.matches("com.intellij.package2.Test"));
    assertFalse(classesFilter.matches("com.intellij.package2.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package3.package4"));
    assertFalse(classesFilter.matches("com.intellij.package3.package5.package4"));
    assertTrue(classesFilter.matches("com.intellij.package3"));
    assertTrue(classesFilter.matches("com.intellij"));
    assertTrue(classesFilter.matches("com.intellij.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test"));
    assertFalse(classesFilter.matches("com.intellij.package5.Test2"));
    assertTrue(classesFilter.matches("com.intellij.package6.Test"));
    assertFalse(classesFilter.matches("com.intellij.package6.ExcludedTest"));
    assertFalse(classesFilter.matches("com.intellij.package7.package8"));
    assertFalse(classesFilter.matches("com.intellij.package7.package5.package8"));
    assertTrue(classesFilter.matches("com.intellij.package7"));
  }
}
