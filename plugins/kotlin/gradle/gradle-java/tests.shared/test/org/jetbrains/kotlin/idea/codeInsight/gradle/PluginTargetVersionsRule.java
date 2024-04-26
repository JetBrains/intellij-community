// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle;

import org.gradle.util.GradleVersion;
import org.hamcrest.CustomMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.gradle.multiplatformTests.TestWithKotlinPluginAndGradleVersions;
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher;
import org.junit.AssumptionViolatedException;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import static com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY;


public class PluginTargetVersionsRule implements MethodRule {
    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static class TargetVersionsImpl implements TargetVersions {
        private final String[] value;

        TargetVersionsImpl(String... value) {
            this.value = value;
        }

        @Override
        public String[] value() {
            return value;
        }

        @Override
        public boolean checkBaseVersions() {
            return true;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }
    }

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        final PluginTargetVersions targetVersions = method.getAnnotation(PluginTargetVersions.class);
        if (method.getAnnotation(TargetVersions.class) != null && targetVersions != null) {
            throw new IllegalArgumentException(
                    String.format("Annotations %s and %s could not be used together. ",
                                  TargetVersions.class.getName(), PluginTargetVersions.class.getName())
            );
        }

        TestWithKotlinPluginAndGradleVersions testCase = (TestWithKotlinPluginAndGradleVersions) target;
        if (targetVersions != null && !shouldRun(targetVersions, testCase)) {
            String mark = IS_UNDER_TEAMCITY ? "passed" : "ignored";
            String message = "Test is marked " + mark + " due to unmet requirements\n" +
                             "Gradle version: " +
                             testCase.getGradleVersion() +
                             " | Requirement: " +
                             targetVersions.gradleVersion() +
                             "\n" +
                             "Plugin version: " +
                             testCase.getKotlinPluginVersion() +
                             " | Requirement: " +
                             targetVersions.pluginVersion();

            /*
             Tests are marked as successful on CI instead of Ignored, because this makes overall project maintainance easier
             (managing legitimately ignored tests).
             Running tests locally will still mark them as 'Ignored'
             */
            if (IS_UNDER_TEAMCITY) {
                return new Statement() {
                    @Override
                    public void evaluate() {
                        System.out.println(message);
                    }
                };
            } else {
                throw new AssumptionViolatedException(message);
            }
        }

        return base;
    }

    private static boolean shouldRun(PluginTargetVersions targetVersions, TestWithKotlinPluginAndGradleVersions testCase) {
        var gradleVersion = testCase.getGradleVersion();
        var pluginVersion = testCase.getKotlinPluginVersion();

        var gradleVersionMatcher = createMatcher("Gradle", targetVersions.gradleVersion());
        var kotlinVersionRequirement = KotlinVersionUtils.parseKotlinVersionRequirement(targetVersions.pluginVersion());

        boolean matchGradleVersion = gradleVersionMatcher == null || gradleVersionMatcher.matches(gradleVersion);
        return matchGradleVersion && KotlinVersionUtils.matches(kotlinVersionRequirement, pluginVersion);
    }

    @Nullable
    private static CustomMatcher<String> createMatcher(@NotNull String caption, @NotNull String version) {
        if (version.isEmpty()) {
            return null;
        }

        TargetVersions targetVersions = new TargetVersionsImpl(version);

        return new CustomMatcher<>(caption + " version '" + Arrays.toString(targetVersions.value()) + "'") {
            @Override
            public boolean matches(Object item) {
                return item instanceof String && new VersionMatcher(GradleVersion.version(item.toString())).isVersionMatch(targetVersions);
            }
        };
    }
}
