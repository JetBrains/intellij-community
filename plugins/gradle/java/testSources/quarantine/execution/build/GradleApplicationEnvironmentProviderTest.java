// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.quarantine.execution.build;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.PlatformTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.plugins.gradle.execution.build.GradleApplicationEnvironmentProviderTestCase;
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions;
import org.junit.Test;

/**
 * @author Vladislav.Soroka
 */
public class GradleApplicationEnvironmentProviderTest extends GradleApplicationEnvironmentProviderTestCase {

  @Test
  @TargetVersions("7.0+") // full JPMS support added by Gradle starting from Gradle 7.0
  public void testConfigurationsShouldNotBeResolvedDuringTheProjectEvaluation() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
    @Language("Java")
    String appClass = """
      package my;
      public class Application {       
          public static void main(String[] args){
              System.out.println("Hello expected world");
          }
      }
      """;
    createProjectSubFile("src/main/java/my/Application.java", appClass);
    createProjectSubFile("src/main/java/module-info.java", "module my {}");

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
          "           mainClass = 'my.Application'",
          "           moduleName = 'moduleName.main'",
          "       }",
          "    }",
          "  }",
          "}")
        .addPostfix(
          """
            afterEvaluate {
                configurations.runtimeOnly {
                    extendsFrom(configurations.compileOnly)
                }
            }
            """
        ).generate()
    );
    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "Hello expected world");
  }

  @Test
  @TargetVersions("4.7+") // The idea ext plugin is only compatible with Gradle 4.7+
  public void testApplicationRunConfigurationSettingsImport() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
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
    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    ApplicationConfiguration configuration = (ApplicationConfiguration)configurationSettings.getConfiguration();

    String appArgs = "Passed arguments: [foo, --bar, baz]";
    System.out.println("Check ShortenCommandLine.NONE");
    configuration.setShortenCommandLine(ShortenCommandLine.NONE);
    assertAppRunOutput(configurationSettings, appArgs);

    System.out.println("Check ShortenCommandLine.MANIFEST");
    configuration.setShortenCommandLine(ShortenCommandLine.MANIFEST);
    assertAppRunOutput(configurationSettings, appArgs);

    Sdk jdk = JavaParametersUtil.createProjectJdk(getMyProject(), configuration.getAlternativeJrePath());
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
  @TargetVersions("6.4+") // initial JPMS support added by Gradle starting from Gradle 6.4
  public void testJavaModuleRunConfigurationWithResources() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
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

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "File Content: content");
  }

  @Test
  @TargetVersions("7.0+") // full JPMS support added by Gradle starting from Gradle 7.0
  public void testJavaModuleRunConfigurationWithProvider() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
    @Language("Java") String appClass = """
      package serviceloader;
      
      import java.util.ServiceLoader;
      
      public class App {
          public static void main(String[] args) {
              var greeter = ServiceLoader.load(GreeterService.class).findFirst().orElseThrow();
              greeter.greet();
          }
      
          public static class DefaultGreeterService implements GreeterService {
              @Override
              public void greet() { System.err.println("I'm from the provider!"); }
          }
      
          public interface GreeterService {
              void greet();
          }
      }
      """;
    createProjectSubFile("src/main/java/my/App.java", appClass);
    @Language("Java") final String module = """
      module my.test {
      	uses serviceloader.App.GreeterService;
      	provides serviceloader.App.GreeterService with serviceloader.App.DefaultGreeterService;
      }
      """;
    createProjectSubFile("src/main/java/module-info.java", module);

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
          "           mainClass = 'serviceloader.App'",
          "           moduleName = 'moduleName.main'",
          "       }",
          "    }",
          "  }",
          "}")
        .generate()
    );

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "I'm from the provider!");
  }

  @Test
  @TargetVersions("4.7+") // The idea ext plugin is only compatible with Gradle 4.7+
  public void testRunApplicationInnerStaticClass() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
    @Language("Java")
    String appClass = """
      package my;

      public class Outer {
        public static class Inner {
          public static void main(String[] args){
            System.out.println("Hello expected world");
          }
        }
      }
      """;
    createProjectSubFile("src/main/java/my/Outer.java", appClass);

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
          "           mainClass = 'my.Outer$Inner'",
          "           moduleName = 'moduleName.main'",
          "       }",
          "    }",
          "  }",
          "}")
        .generate()
    );

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "Hello expected world");
  }

  @Test
  // A task could be executed via included task fqdn like :root:something:something only starting from Gradle 6.8
  // This behavior for older Gradle version is broken due to IDEA-382710
  @TargetVersions("6.8+")
  public void testRunApplicationInNestedComposite() throws Exception {
    PlatformTestUtil.getOrCreateProjectBaseDir(getMyProject());
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

    RunnerAndConfigurationSettings configurationSettings = RunManager.getInstance(getMyProject()).findConfigurationByName("MyApp");
    assertAppRunOutput(configurationSettings, "Hello expected world");
  }
}