// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight.gradle;

import org.gradle.util.GradleVersion;
import org.hamcrest.CustomMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.util.KotlinVersionUtils;
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher;
import org.junit.AssumptionViolatedException;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;


public class PluginTargetVersionsRule implements MethodRule {
    @SuppressWarnings("ClassExplicitlyAnnotation")
    private static class TargetVersionsImpl implements TargetVersions {
        private final String value;

        TargetVersionsImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
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

        MultiplePluginVersionGradleImportingTestCase testCase = (MultiplePluginVersionGradleImportingTestCase) target;
        if (targetVersions != null && !shouldRun(targetVersions, testCase)) {
            return new Statement() {
                @Override
                public void evaluate() {
                    throw new AssumptionViolatedException(
                            "Test is ignored\n" +
                            "Gradle version: " +
                            testCase.gradleVersion +
                            " | Requirement: " +
                            targetVersions.gradleVersion() +
                            "\n" +
                            "Plugin version: " +
                            testCase.getKotlinPluginVersionString() +
                            " | Requirement: " +
                            targetVersions.pluginVersion()
                    );
                }
            };
        }

        return base;
    }

    private static boolean shouldRun(PluginTargetVersions targetVersions, MultiplePluginVersionGradleImportingTestCase testCase) {
        var gradleVersion = testCase.gradleVersion;
        var pluginVersion = testCase.getKotlinPluginVersionString();

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

        return new CustomMatcher<>(caption + " version '" + targetVersions.value() + "'") {
            @Override
            public boolean matches(Object item) {
                return item instanceof String && new VersionMatcher(GradleVersion.version(item.toString())).isVersionMatch(targetVersions);
            }
        };
    }
}
