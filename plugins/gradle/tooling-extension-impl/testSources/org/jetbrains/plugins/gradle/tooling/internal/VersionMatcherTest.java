// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal;

import org.gradle.api.Project;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VersionMatcherTest {

  @Test
  public void testVersionMatching() {

    assertTrue(isMatching("1.8", TestModelBuilderService_v_1_8.class));
    assertTrue(isMatching("1.8-rc-1", TestModelBuilderService_v_1_8.class));
    assertFalse(isMatching("1.7", TestModelBuilderService_v_1_8.class));
    assertFalse(isMatching("1.7-rc-1", TestModelBuilderService_v_1_8.class));
    assertFalse(isMatching("1.9", TestModelBuilderService_v_1_8.class));

    assertTrue(isMatching("1.8", TestModelBuilderService_v_1_8_or_Above.class));
    assertTrue(isMatching("1.8-rc-1", TestModelBuilderService_v_1_8_or_Above.class));
    assertFalse(isMatching("1.7", TestModelBuilderService_v_1_8_or_Above.class));
    assertTrue(isMatching("1.9", TestModelBuilderService_v_1_8_or_Above.class));
    assertTrue(isMatching("1.10", TestModelBuilderService_v_1_8_or_Above.class));

    assertTrue(isMatching("1.8", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertTrue(isMatching("1.8-20130903233633+0000", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertFalse(isMatching("1.7", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertFalse(isMatching("1.7-20130903233633+0000", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertTrue(isMatching("1.9", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertTrue(isMatching("1.9-20131007220022+0000", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertFalse(isMatching("1.10", TestModelBuilderService_between_v_1_8_and_1_9.class));
    assertFalse(isMatching("1.10-20131007220022+0000", TestModelBuilderService_between_v_1_8_and_1_9.class));

    assertTrue(isMatching("1.8", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertFalse(isMatching("1.8-20130903233633+0000", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertFalse(isMatching("1.7", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertFalse(isMatching("1.7-20130903233633+0000", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertTrue(isMatching("1.9", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertTrue(isMatching("1.9-20131007220022+0000", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertFalse(isMatching("1.10", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));
    assertFalse(isMatching("1.10-20131007220022+0000", TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class));

    assertTrue(isMatching("1.1", Not_1_0.class));
    assertFalse(isMatching("1.0", Not_1_0.class));
    assertTrue(isMatching("1.0.1", Not_1_0.class));
    assertTrue(isMatching("0.9", Not_1_0.class));

    assertTrue(isMatching("4.6", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("4.7", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("7.0", v_atLeast_4_6_and_not_6_9.class));
    assertFalse(isMatching("4.5", v_atLeast_4_6_and_not_6_9.class));
    assertFalse(isMatching("1.0", v_atLeast_4_6_and_not_6_9.class));
    assertFalse(isMatching("6.9", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("6.9.0", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("6.9.5", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("6.8", v_atLeast_4_6_and_not_6_9.class));
    assertTrue(isMatching("6.10", v_atLeast_4_6_and_not_6_9.class));
  }

  @Test
  public void testPatchVersionMatching() {
    // exact version with patch - only matches that specific patch
    assertTrue(isMatching("1.8.0", v_1_8_0.class));
    assertFalse(isMatching("1.8.1", v_1_8_0.class));
    assertFalse(isMatching("1.8", v_1_8_0.class));
    assertFalse(isMatching("1.7.0", v_1_8_0.class));

    // version without patch (no .x wildcard) - exact match only
    assertTrue(isMatching("1.8", v_1_8.class));
    assertFalse(isMatching("1.8.0", v_1_8.class));
    assertFalse(isMatching("1.8.5", v_1_8.class));
    assertFalse(isMatching("1.7", v_1_8.class));
    assertFalse(isMatching("1.9", v_1_8.class));

    // negation with patch - excludes only that specific patch
    assertTrue(isMatching("1.8.0", v_not_1_8_1.class));
    assertFalse(isMatching("1.8.1", v_not_1_8_1.class));
    assertTrue(isMatching("1.8.2", v_not_1_8_1.class));
    assertTrue(isMatching("1.7", v_not_1_8_1.class));
  }

  @Test
  public void testWildcardPatchVersionMatching() {
    assertTrue(isMatching("1.8", v_1_8_x.class));
    assertTrue(isMatching("1.8.0", v_1_8_x.class));
    assertTrue(isMatching("1.8.1", v_1_8_x.class));
    assertTrue(isMatching("1.8.99", v_1_8_x.class));
    assertFalse(isMatching("1.7", v_1_8_x.class));
    assertFalse(isMatching("1.7.99", v_1_8_x.class));
    assertFalse(isMatching("1.9", v_1_8_x.class));
    assertFalse(isMatching("1.9.0", v_1_8_x.class));

    assertTrue(isMatching("1.8", v_from_1_8_x_to_1_9_x.class));
    assertTrue(isMatching("1.8.0", v_from_1_8_x_to_1_9_x.class));
    assertTrue(isMatching("1.8.5", v_from_1_8_x_to_1_9_x.class));
    assertTrue(isMatching("1.9", v_from_1_8_x_to_1_9_x.class));
    assertTrue(isMatching("1.9.5", v_from_1_8_x_to_1_9_x.class));
    assertFalse(isMatching("1.7", v_from_1_8_x_to_1_9_x.class));
    assertFalse(isMatching("1.7.99", v_from_1_8_x_to_1_9_x.class));
    assertFalse(isMatching("1.10", v_from_1_8_x_to_1_9_x.class));
    assertFalse(isMatching("1.10.0", v_from_1_8_x_to_1_9_x.class));

    assertTrue(isMatching("1.7", v_less_than_or_equal_1_8_x.class));
    assertTrue(isMatching("1.8", v_less_than_or_equal_1_8_x.class));
    assertTrue(isMatching("1.8.0", v_less_than_or_equal_1_8_x.class));
    assertTrue(isMatching("1.8.99", v_less_than_or_equal_1_8_x.class));
    assertFalse(isMatching("1.9", v_less_than_or_equal_1_8_x.class));
    assertFalse(isMatching("1.9.0", v_less_than_or_equal_1_8_x.class));

    assertTrue(isMatching("1.7", v_not_1_8_x.class));
    assertTrue(isMatching("1.7.99", v_not_1_8_x.class));
    assertFalse(isMatching("1.8", v_not_1_8_x.class));
    assertFalse(isMatching("1.8.0", v_not_1_8_x.class));
    assertFalse(isMatching("1.8.99", v_not_1_8_x.class));
    assertTrue(isMatching("1.9", v_not_1_8_x.class));
    assertTrue(isMatching("1.9.0", v_not_1_8_x.class));
  }

  @TargetVersions("1.8")
  public static class TestModelBuilderService_v_1_8 implements ModelBuilderService {
    @Override
    public boolean canBuild(String modelName) {
      return TestModelBuilderService_v_1_8.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
      return null;
    }

    @Override
    public void reportErrorMessage(
      @NotNull String modelName,
      @NotNull Project project,
      @NotNull ModelBuilderContext context,
      @NotNull Exception exception
    ) {
      context.getMessageReporter().createMessage()
        .withGroup("gradle.test.group")
        .withException(exception)
        .reportMessage(project);
    }
  }

  @TargetVersions("1.8+")
  public static class TestModelBuilderService_v_1_8_or_Above implements ModelBuilderService {
    @Override
    public boolean canBuild(String modelName) {
      return TestModelBuilderService_v_1_8_or_Above.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
      return null;
    }

    @Override
    public void reportErrorMessage(
      @NotNull String modelName,
      @NotNull Project project,
      @NotNull ModelBuilderContext context,
      @NotNull Exception exception
    ) {
      context.getMessageReporter().createMessage()
        .withGroup("gradle.test.group")
        .withException(exception)
        .reportMessage(project);
    }
  }

  @TargetVersions("1.8 <=> 1.9")
  public static class TestModelBuilderService_between_v_1_8_and_1_9 implements ModelBuilderService {
    @Override
    public boolean canBuild(String modelName) {
      return TestModelBuilderService_between_v_1_8_and_1_9.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
      return null;
    }

    @Override
    public void reportErrorMessage(
      @NotNull String modelName,
      @NotNull Project project,
      @NotNull ModelBuilderContext context,
      @NotNull Exception exception
    ) {
      context.getMessageReporter().createMessage()
        .withGroup("gradle.test.group")
        .withException(exception)
        .reportMessage(project);
    }
  }

  @TargetVersions(value = "1.8 <=> 1.9", checkBaseVersions = false)
  public static class TestModelBuilderService_between_v_1_8_and_1_9_NotBase implements ModelBuilderService {
    @Override
    public boolean canBuild(String modelName) {
      return TestModelBuilderService_between_v_1_8_and_1_9_NotBase.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
      return null;
    }

    @Override
    public void reportErrorMessage(
      @NotNull String modelName,
      @NotNull Project project,
      @NotNull ModelBuilderContext context,
      @NotNull Exception exception
    ) {
      context.getMessageReporter().createMessage()
        .withGroup("gradle.test.group")
        .withException(exception)
        .reportMessage(project);
    }
  }

  @TargetVersions("!1.0")
  public static class Not_1_0 {
  }

  @TargetVersions({"4.6+", "!6.9"})
  public static class v_atLeast_4_6_and_not_6_9 {
  }

  @TargetVersions("1.8.0")
  public static class v_1_8_0 {
  }

  @TargetVersions("1.8")
  public static class v_1_8 {
  }

  @TargetVersions("1.8.x")
  public static class v_1_8_x {
  }

  @TargetVersions("!1.8.1")
  public static class v_not_1_8_1 {
  }

  @TargetVersions("1.8.x <=> 1.9.x")
  public static class v_from_1_8_x_to_1_9_x {
  }

  @TargetVersions("<=1.8.x")
  public static class v_less_than_or_equal_1_8_x {
  }

  @TargetVersions("!1.8.x")
  public static class v_not_1_8_x {
  }

  private static boolean isMatching(String version, Class<?> aClass) {
    final TargetVersions annotation = aClass.getAnnotation(TargetVersions.class);
    if (annotation == null) {
      return false;
    }
    return new VersionMatcher(GradleVersion.version(version)).isVersionMatch(annotation);
  }
}
