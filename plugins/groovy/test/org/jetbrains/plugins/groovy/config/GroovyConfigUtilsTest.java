// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.groovy.config.AbstractConfigUtils.VERSION_GROUP_NAME;
import static org.jetbrains.plugins.groovy.config.GroovyConfigUtils.GROOVY_ALL_JAR_PATTERN;
import static org.jetbrains.plugins.groovy.config.GroovyConfigUtils.GROOVY_JAR_PATTERN;

public class GroovyConfigUtilsTest {
  @Test
  public void testGroovyJarPattern() {
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy-.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString-.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString-42.jar"));
  }

  @Test
  public void testGroovyJarPatternIndy() {
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-indy.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy--indy.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-indy.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5--indy.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString-indy.jar"));
    Assert.assertFalse(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString--indy.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.2.3.4.5-someString-42-indy.jar"));
  }

  @Test
  public void testGroovyJarPatternExistingExamples() {
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-1.0.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-2.4.15.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-2.4.16-SNAPSHOT.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-2.5.0-rc-2.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-2.6.0-beta-2.jar"));
    Assert.assertTrue(matches(GROOVY_JAR_PATTERN, "groovy-3.0.0-alpha-2.jar"));
  }

  @Test
  public void testGroovyAllJarPattern() {
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString-42.jar"));
  }

  @Test
  public void testGroovyAllJarPatternIndy() {
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-1.2.3.4.5-someString-42-indy.jar"));
  }

  @Test
  public void testGroovyAllJarPatternMinimal() {
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString-.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString-42.jar"));
  }

  @Test
  public void testGroovyAllJarPatternMinimalIndy() {
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString-indy.jar"));
    Assert.assertFalse(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString--indy.jar"));
    Assert.assertTrue(matches(GROOVY_ALL_JAR_PATTERN, "groovy-all-minimal-1.2.3.4.5-someString-42-indy.jar"));
  }

  @Test
  public void groovy_all_jar_pattern_existing_examples() {
    testGroovyAllVersion("groovy-all-1.0.jar", "1.0");
    testGroovyAllVersion("groovy-all-2.4.15.jar", "2.4.15");
    testGroovyAllVersion("groovy-all-2.4.16-SNAPSHOT.jar", "2.4.16-SNAPSHOT");
    testGroovyAllVersion("groovy-all-2.5.0-rc-2.jar", "2.5.0-rc-2");
    testGroovyAllVersion("groovy-all-2.6.0-beta-2.jar", "2.6.0-beta-2");
    testGroovyAllVersion("groovy-all-3.0.0-alpha-2.jar", "3.0.0-alpha-2");

    testGroovyAllVersion("groovy-all-1.0-indy.jar", "1.0");
    testGroovyAllVersion("groovy-all-2.4.15-indy.jar", "2.4.15");
    testGroovyAllVersion("groovy-all-2.4.16-SNAPSHOT-indy.jar", "2.4.16-SNAPSHOT");
    testGroovyAllVersion("groovy-all-2.5.0-rc-2-indy.jar", "2.5.0-rc-2");
    testGroovyAllVersion("groovy-all-2.6.0-beta-2-indy.jar", "2.6.0-beta-2");
    testGroovyAllVersion("groovy-all-3.0.0-alpha-2-indy.jar", "3.0.0-alpha-2");

    testGroovyAllVersion("groovy-all-minimal-1.0.jar", "1.0");
    testGroovyAllVersion("groovy-all-minimal-2.4.15.jar", "2.4.15");
    testGroovyAllVersion("groovy-all-minimal-2.4.16-SNAPSHOT.jar", "2.4.16-SNAPSHOT");
    testGroovyAllVersion("groovy-all-minimal-2.5.0-rc-2.jar", "2.5.0-rc-2");
    testGroovyAllVersion("groovy-all-minimal-2.6.0-beta-2.jar", "2.6.0-beta-2");
    testGroovyAllVersion("groovy-all-minimal-3.0.0-alpha-2.jar", "3.0.0-alpha-2");

    testGroovyAllVersion("groovy-all-minimal-1.0-indy.jar", "1.0");
    testGroovyAllVersion("groovy-all-minimal-2.4.15-indy.jar", "2.4.15");
    testGroovyAllVersion("groovy-all-minimal-2.4.16-SNAPSHOT-indy.jar", "2.4.16-SNAPSHOT");
    testGroovyAllVersion("groovy-all-minimal-2.5.0-rc-2-indy.jar", "2.5.0-rc-2");
    testGroovyAllVersion("groovy-all-minimal-2.6.0-beta-2-indy.jar", "2.6.0-beta-2");
    testGroovyAllVersion("groovy-all-minimal-3.0.0-alpha-2-indy.jar", "3.0.0-alpha-2");
  }

  private static void testGroovyAllVersion(String jarName, String expectedVersion) {
    Matcher matcher = GROOVY_ALL_JAR_PATTERN.matcher(jarName);
    Assert.assertTrue(matcher.matches());
    String version = matcher.group(VERSION_GROUP_NAME);
    Assert.assertEquals(expectedVersion, version);
  }

  private static boolean matches(@NotNull Pattern pattern, @NotNull String text) {
    return pattern.matcher(text).matches();
  }

}
