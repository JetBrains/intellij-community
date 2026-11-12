// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParsingFixtureKt.groovyParsingFixture;

@TestApplication
public class SealedParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "types/sealed");

  @Test
  public void testBasicNonsealed() { parsing.get().doTest(); }

  @Test
  public void testBasicSealed() { parsing.get().doTest(); }

  @Test
  public void testEnum() { parsing.get().doTest(); }

  @Test
  public void testExplicitSubclasses() { parsing.get().doTest(); }

  @Test
  public void testInterface() { parsing.get().doTest(); }

  @Test
  public void testPermitsAfterExtends() { parsing.get().doTest(); }

  @Test
  public void testPermitsAfterImplements() { parsing.get().doTest(); }

  @Test
  public void testPermitsBeforeExtends() { parsing.get().doTest(); }

  @Test
  public void testPermitsBeforeImplements() { parsing.get().doTest(); }

  @Test
  public void testTrait() { parsing.get().doTest(); }
}
