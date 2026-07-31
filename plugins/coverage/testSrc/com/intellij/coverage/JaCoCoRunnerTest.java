// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.execution.configurations.RunConfigurationOptions;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.java.JavaTargetParameter;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@RunWith(JUnit4.class)
public class JaCoCoRunnerTest extends HeavyPlatformTestCase {
  @Test
  public void excludeIncludePatterns() {
    SimpleJavaParameters javaParameters = new SimpleJavaParameters();
    new JaCoCoCoverageRunner().appendCoverageArgument("a", null, new String[]{"org.*", "com.*"}, javaParameters, true, true, null, null);
    Assert.assertTrue(Pattern.compile("-javaagent:(.*)jacocoagent(.*).jar=destfile=a,append=false,inclnolocationclasses=true,excludes=org\\.\\*:com\\.\\*")
                        .matcher(String.join("", javaParameters.getTargetDependentParameters().toLocalParameters())).matches());
  }

  @Test
  public void includeAndExcludePatterns() {
    JavaTargetParameter parameter = new JaCoCoCoverageRunner().createArgumentTargetValue("jacocoagent.jar",
                                                                                        "coverage.exec",
                                                                                        new String[]{"foo.*", "bar.Baz"},
                                                                                        new String[]{"org.*"});
    Assert.assertEquals("-javaagent:jacocoagent.jar=destfile=coverage.exec,append=false,inclnolocationclasses=true,includes=foo.*:bar.Baz,excludes=org.*",
                        parameter.toLocalParameter());
  }

  @Test
  public void modulesFollowRunConfigurationModuleDependencies() {
    Module dependency = createModule("dependency");
    ModuleRootModificationUtil.addDependency(getModule(), dependency);
    TestModuleBasedConfiguration configuration = new TestModuleBasedConfiguration(getProject(), getModule());

    Assert.assertEquals(List.of(getModule(), dependency), BaseCoverageSuite.getRelatedModules(configuration));
  }

  @Test
  public void canBeLoadedAcceptsJaCoCoExecReport() {
    File report = new File(PluginPathManager.getPluginHomePath("coverage"), "testData/simple/simple$foo_in_simple.exec");

    Assert.assertTrue(new JaCoCoCoverageRunner().canBeLoaded(report.toPath()));
  }

  @Test
  public void canBeLoadedRejectsInvalidReport() throws IOException {
    File report = FileUtil.createTempFile("invalid-jacoco", ".exec", true);
    FileUtil.writeToFile(report, "not a jacoco report".getBytes(StandardCharsets.UTF_8));

    Assert.assertFalse(new JaCoCoCoverageRunner().canBeLoaded(report.toPath()));
  }

  private static final class TestModuleBasedConfiguration
    extends ModuleBasedConfiguration<RunConfigurationModule, RunConfigurationOptions> {

    private TestModuleBasedConfiguration(@NotNull Project project, @NotNull Module module) {
      super("test", new RunConfigurationModule(project), UnknownConfigurationType.getInstance().getConfigurationFactories()[0]);
      setModule(module);
    }

    @Override
    public @NotNull Collection<Module> getValidModules() {
      return getAllModules();
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
      return null;
    }
  }
}
