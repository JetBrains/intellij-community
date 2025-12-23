// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test;

import com.intellij.openapi.Disposable;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity;
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation;
import org.jetbrains.kotlin.cli.common.messages.MessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.cli.pipeline.AbstractConfigurationPhaseKt;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts;
import org.jetbrains.kotlin.test.TestJdkKind;

import java.io.File;
import java.util.*;

import static org.jetbrains.kotlin.idea.test.KotlinTestUtils.getCurrentProcessJdkHome;

public final class KotlinTestUtilsImpl {
    public static final String TEST_MODULE_NAME = "test-module";

    @NotNull
    public static KotlinCoreEnvironment createEnvironmentWithJdkAndNullabilityAnnotationsFromIdea(
            @NotNull Disposable disposable,
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind
    ) {
        return KotlinCoreEnvironment.createForTests(
                disposable, newConfiguration(configurationKind, jdkKind, TestKotlinArtifacts.getJetbrainsAnnotations().toFile()),
                EnvironmentConfigFiles.JVM_CONFIG_FILES
        );
    }

    public static File findMockJdkRtJar() {
        return new File(IdeaTestUtil.getMockJdk18Path().getAbsolutePath(), "jre/lib/rt.jar");
    }

    @NotNull
    public static CompilerConfiguration newConfiguration() {
        CompilerConfiguration configuration = new CompilerConfiguration();
        configuration.put(CommonConfigurationKeys.MODULE_NAME, TEST_MODULE_NAME);

        configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, new MessageCollector() {
            @Override
            public void clear() {
            }

            @Override
            public void report(
                    @NotNull CompilerMessageSeverity severity, @NotNull String message, @Nullable CompilerMessageSourceLocation location
            ) {
                if (severity == CompilerMessageSeverity.ERROR) {
                    String prefix = location == null
                                    ? ""
                                    : "(" + location.getPath() + ":" + location.getLine() + ":" + location.getColumn() + ") ";
                    throw new AssertionError(prefix + message);
                }
            }

            @Override
            public boolean hasErrors() {
                return false;
            }
        });

        return configuration;
    }

    @NotNull
    public static CompilerConfiguration newConfiguration(
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind,
            @NotNull List<File> classpath,
            @NotNull List<File> javaSource
    ) {
        CompilerConfiguration configuration = newConfiguration();
        JvmContentRootsKt.addJavaSourceRoots(configuration, javaSource);
        if (jdkKind == TestJdkKind.MOCK_JDK) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, findMockJdkRtJar());
            configuration.put(JVMConfigurationKeys.NO_JDK, true);
        } else {
            configuration.put(JVMConfigurationKeys.JDK_HOME, getCurrentProcessJdkHome());
        }

        if (configurationKind.getKotlinStdlib()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinStdlib().toFile());
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinScriptRuntime().toFile());
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinTest().toFile());
            configuration.put(CLIConfigurationKeys.PATH_TO_KOTLIN_COMPILER_JAR, TestKotlinArtifacts.getKotlinCompiler().toFile());
        }

        if (configurationKind.getKotlinReflect()) {
            JvmContentRootsKt.addJvmClasspathRoot(configuration, TestKotlinArtifacts.getKotlinReflect().toFile());
        }

        JvmContentRootsKt.addJvmClasspathRoots(configuration, classpath);

        configuration.put(
                CLIConfigurationKeys.INTELLIJ_PLUGIN_ROOT,
                TestKotlinArtifacts.getKotlinCompiler().toFile().getAbsolutePath()
        );

        setupIdeaStandaloneExecution();
        AbstractConfigurationPhaseKt.registerExtensionStorage(configuration);

        return configuration;
    }

    private static void setupIdeaStandaloneExecution() {
        System.getProperties().setProperty("psi.incremental.reparse.depth.limit", "1000");
    }


    @NotNull
    public static CompilerConfiguration newConfiguration(
            @NotNull ConfigurationKind configurationKind,
            @NotNull TestJdkKind jdkKind,
            @NotNull File... extraClasspath
    ) {
        return newConfiguration(configurationKind, jdkKind, Arrays.asList(extraClasspath), Collections.emptyList());
    }

}
