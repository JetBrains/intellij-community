// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.build;

import com.intellij.execution.*;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.Semaphore;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleSettingsImportingTestCase;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 */
public class GradleApplicationEnvironmentProviderTest extends GradleSettingsImportingTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCurrentExternalProjectSettings().setDelegatedBuild(true);
  }

  @Test
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(myProject);
    @Language("Java")
    String appClass = """
      package my;
      import java.util.Arrays;

      public class App {
          public static void main(String[] args) {
              System.out.println("Class-Path: " + System.getProperty("java.class.path"));
              System.out.println("Passed arguments: " + Arrays.toString(args));
          }
      }
      """;
    createProjectSubFile("src/main/java/my/App.java", appClass);
    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withIdeaPlugin()
        .withGradleIdeaExtPlugin()
        .addImport("org.jetbrains.gradle.ext.*")
        .addPostfix(
          "idea {",
          "  project.settings {",
          "    runConfigurations {",
          "       MyApp(Application) {",
          "           mainClass = 'my.App'",
          "           programParameters = 'foo --bar baz'",
          "           moduleName = 'moduleName.main'",
          "       }",
          "    }",
          "  }",
          "}")
        .generate()
    );

    assertModules("moduleName", "moduleName.main", "moduleName.test");
    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(myProject).findConfigurationByName("MyApp");
    ApplicationConfiguration configuration = (ApplicationConfiguration)configurationSettings.getConfiguration();

    String appArgs = "Passed arguments: [foo, --bar, baz]";
    System.out.println("Check ShortenCommandLine.NONE");
    configuration.setShortenCommandLine(ShortenCommandLine.NONE);
    assertAppRunOutput(configurationSettings, appArgs);

    System.out.println("Check ShortenCommandLine.MANIFEST");
    configuration.setShortenCommandLine(ShortenCommandLine.MANIFEST);
    assertAppRunOutput(configurationSettings, appArgs);

    Sdk jdk = JavaParametersUtil.createProjectJdk(myProject, configuration.getAlternativeJrePath());
    if (JavaSdk.getInstance().isOfVersionOrHigher(jdk, JavaSdkVersion.JDK_1_9)) {
      System.out.println("Check ShortenCommandLine.ARGS_FILE");
      configuration.setShortenCommandLine(ShortenCommandLine.ARGS_FILE);
      assertAppRunOutput(configurationSettings, appArgs);
    }
    else {
      System.out.println("Check ShortenCommandLine.CLASSPATH_FILE");
      configuration.setShortenCommandLine(ShortenCommandLine.CLASSPATH_FILE);
      assertAppRunOutput(configurationSettings, appArgs);
    }
  }

  @Test
  public void testJavaModuleRunConfiguration() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(myProject);
    @Language("Java")
    String appClass = """
      package my;
      import java.io.BufferedReader;
      import java.io.IOException;
      import java.io.InputStream;
      import java.io.InputStreamReader;
      import java.nio.charset.StandardCharsets;


      public class App {
          public static void main(String[] args) throws IOException {
            String fileContent = new App().readFile();
            System.out.println("File Content: " + fileContent);
          }
         \s
          public String readFile() throws IOException {
            try (InputStream is =
                   getClass().getClassLoader().getResourceAsStream("file.txt")) {
        if (is == null) return null;
              BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
              return bufferedReader.readLine();
            }
          }
      }
      """;
    createProjectSubFile("src/main/java/my/App.java", appClass);
    @Language("Java")
    final String module = """
      module my {
       exports my;
      }""";
    createProjectSubFile("src/main/java/module-info.java", module);
    createProjectSubFile("src/main/resources/file.txt", "content\n");

    createSettingsFile("rootProject.name = 'moduleName'");
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .withIdeaPlugin()
        .withGradleIdeaExtPlugin()
        .addImport("org.jetbrains.gradle.ext.*")
        .addPostfix(
          "idea {",
          "  project.settings {",
          "    runConfigurations {",
          "       MyApp(Application) {",
          "           mainClass = 'my.App'",
          "           moduleName = 'moduleName.main'",
          "       }",
          "    }",
          "  }",
          "}")
        .generate()
    );

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(myProject).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "File Content: content");
  }

  @Test
  public void testRunApplicationInNestedComposite() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(myProject);
    @Language("Java")
    String appClass = """
      package my;
      import java.util.Arrays;

      public class App {
          public static void main(String[] args) {
              System.out.println("Hello expected world");
          }
      }
      """;
    createProjectSubFile("nested/src/main/java/my/App.java", appClass);
    createProjectSubFile("settings.gradle", "includeBuild('nested')");
    createProjectSubFile("nested/settings.gradle", "rootProject.name='app'");
    createProjectSubFile("nested/build.gradle",
                         createBuildScriptBuilder()
                           .withJavaPlugin().generate()
);
    createProjectSubFile("build.gradle", createBuildScriptBuilder()
      .withGradleIdeaExtPlugin()
      .addImport("org.jetbrains.gradle.ext.*")
      .addPostfix(
        "idea {",
        "  project.settings {",
        "    runConfigurations {",
        "       MyApp(Application) {",
        "           mainClass = 'my.App'",
        "           moduleName = 'app.main'",
        "       }",
        "    }",
        "  }",
        "}")
      .generate());
    importProject();

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(myProject).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "Hello expected world");
  }

  private static void assertAppRunOutput(RunnerAndConfigurationSettings configurationSettings, String... checks) {
    String output = runAppAndGetOutput(configurationSettings);
    for (String check : checks) {
      assertTrue(String.format("App output should contain substring: %s, but was:\n%s", check, output), output.contains(check));
    }
  }

  @NotNull
  private static String runAppAndGetOutput(RunnerAndConfigurationSettings configurationSettings) {
    final Semaphore done = new Semaphore();
    done.down();
    ExternalSystemProgressNotificationManager notificationManager =
      ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
    StringBuilder out = new StringBuilder();
    ExternalSystemTaskNotificationListenerAdapter listener = new ExternalSystemTaskNotificationListenerAdapter() {
      private volatile ExternalSystemTaskId myId = null;

      @Override
      public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
        if (myId != null) {
          throw new IllegalStateException("This test listener is not supposed to listen to more than 1 task");
        }
        myId = id;
      }

      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        if (!id.equals(myId)) {
          return;
        }
        if (StringUtil.isEmptyOrSpaces(text)) return;
        (stdOut ? System.out : System.err).print(text);
        out.append(text);
      }

      @Override
      public void onEnd(@NotNull ExternalSystemTaskId id) {
        if (!id.equals(myId)) {
          return;
        }
        done.up();
      }
    };

    try {
      notificationManager.addNotificationListener(listener);
      edt(() -> {
        try {
          ExecutionEnvironment environment =
            ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), configurationSettings)
              .contentToReuse(null)
              .dataContext(null)
              .activeTarget()
              .build();
          ProgramRunnerUtil.executeConfiguration(environment, false, true);
        }
        catch (ExecutionException e) {
          fail(e.getMessage());
        }
      });
      Assert.assertTrue(done.waitFor(30000));
    }
    finally {
      notificationManager.removeNotificationListener(listener);
    }
    return out.toString();
  }
}