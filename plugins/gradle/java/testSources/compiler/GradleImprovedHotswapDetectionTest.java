// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskListener;
import com.intellij.task.ProjectTaskManager;
import com.intellij.testFramework.RunAll;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class GradleImprovedHotswapDetectionTest extends GradleDelegatedBuildTestCase {
  @Language("Java")
  private static final String APP_JAVA =
    "package my.pack;\n" +
    "public class App {\n" +
    "  public int method() { return 42; }\n" +
    "}";

  // App.java with a new method added
  @Language("Java")
  private static final String APP_JAVA_WITH_NEW_METHOD =
    "package my.pack;\n" +
    "public class App extends Impl {\n" +
    "  public int method() { return 42; }\n" +
    "  public int methodX() { return 100_000; }\n" +
    "}";

  // App.java with new added NewInnerClass inner class
  @Language("Java")
  private static final String APP_JAVA_WITH_INNER_CLASS =
    "package my.pack;\n" +
    "public class App extends Impl {\n" +
    "  public int method() { return 42; }\n" +
    "  public static class NewInnerClass {\n" +
    "    public void doNothing() {}" +
    "  }" +
    "}";

  // the NewInnerClass method changed from 'doNothing' to 'doSomething'
  @Language("Java")
  private static final String APP_JAVA_WITH_MODIFIED_INNER_CLASS =
    "package my.pack;\n" +
    "public class App extends Impl {\n" +
    "  public int method() { return 42; }\n" +
    "  public static class NewInnerClass {\n" +
    "    public boolean doSomething() { return true; }\n" +
    "  }\n" +
    "}";

  @Language("Java")
  private static final String IMPL_JAVA =
    "package my.pack;\n" +
    "import my.pack.Api;\n" +
    "public class Impl extends Api {}";

  @Language("Java")
  private static final String IMPL_JAVA_WITH_NEW_METHOD =
    "package my.pack;\n" +
    "import my.pack.Api;\n" +
    "public class Impl extends Api {\n" +
    "  public void newImplMethod() {}\n" +
    "}";

  private String mainRoot;
  private String testRoot;
  private String apiMainRoot;
  private String apiTestRoot;
  private String implMainRoot;
  private String implTestRoot;
  private String apiJar;
  private String implJar;

  private VirtualFile appFile;
  private VirtualFile implFile;
  private final List<String> dirtyOutputRoots = new ArrayList<>();
  private final Map<String, Collection<String>> generatedFiles = new HashMap<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    String langPart = isGradleOlderThan("4.0") ? "build/classes" : "build/classes/java";

    mainRoot = langPart + "/main";
    testRoot = langPart + "/test";
    apiMainRoot = "api/" + langPart + "/main";
    apiTestRoot = "api/" + langPart + "/test";
    implMainRoot = "impl/" + langPart + "/main";
    implTestRoot = "impl/" + langPart + "/test";
    apiJar = "api/build/libs/api.jar";
    implJar = "impl/build/libs/impl.jar";

    clearOutputs();
    Registry.get("gradle.improved.hotswap.detection").setValue(true);
    createProject();
    subscribeToProject();
  }

  @Override
  public void tearDown() throws Exception {
    new RunAll(
      Registry.get("gradle.improved.hotswap.detection")::resetToDefault,
      super::tearDown
    ).run();
  }

  @Test
  public void testBuildMainProject() {
    compileModules("project.main");

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrder(
      mainRoot,
      apiMainRoot,
      apiJar,
      implMainRoot,
      implJar);

    assertThat(generatedFiles).containsOnly(
      Map.entry(mainRoot, Set.of("my/pack/App.class", "my/pack/Other.class")),
      Map.entry(apiMainRoot, Set.of("my/pack/Api.class")),
      Map.entry(implMainRoot, Set.of("my/pack/Impl.class"))
    );

    assertCopied(mainRoot + "/my/pack/App.class");
    assertNotCopied(testRoot + "/my/pack/AppTest.class");

    assertCopied(apiMainRoot + "/my/pack/Api.class");
    assertNotCopied(apiTestRoot + "my/pack/ApiTest.class");

    assertCopied(implMainRoot + "/my/pack/Impl.class");
    assertNotCopied(implTestRoot + "/my/pack/ImplTest.class");
  }


  @Test
  public void testBuildTestProject() {
    compileModules("project.test");

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrder(
      mainRoot,
      testRoot,
      apiMainRoot,
      apiJar,
      implMainRoot,
      implJar);

    assertThat(generatedFiles)
      .containsOnly(
        Map.entry(mainRoot, Set.of("my/pack/App.class", "my/pack/Other.class")),
        Map.entry(testRoot, Set.of("my/pack/AppTest.class")),
        Map.entry(apiMainRoot, Set.of("my/pack/Api.class")),
        Map.entry(implMainRoot, Set.of("my/pack/Impl.class"))
      );

    assertCopied(mainRoot + "/my/pack/App.class");
    assertCopied(testRoot + "/my/pack/AppTest.class");

    assertCopied(apiMainRoot + "/my/pack/Api.class");
    assertNotCopied(apiTestRoot + "my/pack/ApiTest.class");

    assertCopied(implMainRoot + "/my/pack/Impl.class");
    assertNotCopied(implTestRoot + "/my/pack/ImplTest.class");
  }

  @Test
  public void testIncrementalBuildMainProjectUpdatedClass() {
    compileModules("project.main");

    setFileContent(appFile, APP_JAVA_WITH_NEW_METHOD, false);

    clearOutputs();
    compileModules("project.main");

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrder(mainRoot);
    assertThat(generatedFiles)
      .containsOnly(Map.entry(mainRoot, Set.of("my/pack/App.class")));
  }

  /**
   * Test that adding a new inner class correctly marks
   * the inner class *.class file as generated
   */
  @Test
  public void testIncrementalBuildMainProjectNewInnerClass() {
    compileModules("project.main");

    setFileContent(appFile, APP_JAVA_WITH_INNER_CLASS, false);

    clearOutputs();
    compileModules("project.main");

    assertThat(generatedFiles).as("Generated files").containsOnly(
      Map.entry(mainRoot, Set.of("my/pack/App.class", "my/pack/App$NewInnerClass.class"))
    );
  }

  /**
   * Test that modifying inner class correctly marks
   * only the inner class *.class file as generated
   */
  @Test
  public void testIncrementalBuildMainProjectUpdateInnerClass() {
    setFileContent(appFile, APP_JAVA_WITH_INNER_CLASS, false);
    compileModules("project.main");


    setFileContent(appFile, APP_JAVA_WITH_MODIFIED_INNER_CLASS, false);


    clearOutputs();
    compileModules("project.main");

    assertThat(generatedFiles).as("Generated files").containsOnly(
      Map.entry(mainRoot, Set.of("my/pack/App$NewInnerClass.class"))
    );
  }

  @Test
  public void testRebuildMainProjectAfterChangingImpl() {
    compileModules("project.main");

    setFileContent(implFile, IMPL_JAVA_WITH_NEW_METHOD, false);

    clearOutputs();
    compileModules("project.main");

    assertThat(dirtyOutputRoots).as("Dirty output roots").containsExactlyInAnyOrder(
      implMainRoot,
      implJar
    );

    // note that the "implJar" is not marked as a generated file
    // this is jar itself is marked as dirty root
    assertThat(generatedFiles).as("Generated files").containsOnly(
      Map.entry(implMainRoot, Set.of("my/pack/Impl.class"))
    );
  }

  @Test
  public void testBuildTestProjectAfterMain() {
    compileModules("project.main");

    clearOutputs();
    compileModules("project.test");

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrder(testRoot);
    assertThat(generatedFiles).as("Generated files").containsOnly(
      Map.entry(testRoot, Set.of("my/pack/AppTest.class"))
    );

    assertCopied(testRoot + "/my/pack/AppTest.class");
    assertNotCopied(apiTestRoot + "/my/pack/ApiTest.class");
    assertNotCopied(implTestRoot + "/my/pack/ImplTest.class");
  }

  @Test
  public void testRebuildMainProjectAfterUndoingChange() {
    compileModules("project.main");

    setFileContent(appFile, "package my.pack;\n" +
                            "public class App {\n" +
                            "  public int method() { return 42; }\n" +
                            "  public int methodX() { return 42; }\n" +
                            "}", false);

    clearOutputs();
    compileModules("project.main");

    // revert file to previous value
    setFileContent(appFile, APP_JAVA, false);

    clearOutputs();
    compileModules("project.main");

    assertThat(dirtyOutputRoots).containsExactly(mainRoot);
    assertThat(generatedFiles).containsOnly(Map.entry(mainRoot, Set.of("my/pack/App.class")));
  }

  @Test
  public void testBuildProjectWithResources() throws IOException {
    compileModules("project.main");
    createProjectSubFile("src/main/resources/runtime.properties",
                         "resourceString=foobar");

    clearOutputs();
    compileModules("project.main");

    assertThat(dirtyOutputRoots).containsExactly("build/resources/main");
    assertThat(generatedFiles).containsOnly(Map.entry("build/resources/main", Set.of("runtime.properties")));
  }

  private void subscribeToProject() {
    MessageBusConnection connection = myProject.getMessageBus().connect(getTestRootDisposable());
    connection.subscribe(ProjectTaskListener.TOPIC, new ProjectTaskListener() {
      @Override
      public void started(@NotNull ProjectTaskContext context) {
        context.enableCollectionOfGeneratedFiles();
      }

      @Override
      public void finished(@NotNull ProjectTaskManager.Result result) {
        result.getContext().getDirtyOutputPaths()
          .ifPresent(paths -> paths
            .map(path -> relativePath(path))
            .forEach(dirtyOutputRoots::add));

        result.getContext().getGeneratedFilesRoots()
          .forEach(generatedRoot -> {
            Set<String> generatedFilesRelativePaths = result.getContext().getGeneratedFilesRelativePaths(generatedRoot)
              .stream()
              .map(path -> PathUtil.toSystemIndependentName(path))
              .collect(Collectors.toSet());
            generatedFiles.put(relativePath(generatedRoot), generatedFilesRelativePaths);
          });
      }
    });
  }

  private void createProject() throws IOException {
    createSettingsFile("include 'api', 'impl' ");
    createProjectSubFile("gradle.properties",
                         "org.gradle.caching=true");

    appFile = createProjectSubFile("src/main/java/my/pack/App.java", APP_JAVA);

    createProjectSubFile("src/main/java/my/pack/Other.java",
                         "package my.pack;\n" +
                         "public class Other {\n" +
                         "  public String method() { return \"foo\"; }\n" +
                         "}");

    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         "package my.pack;\n" +
                         "public class AppTest {\n" +
                         "  public void test() { new App().method(); }\n" +
                         "}");

    createProjectSubFile("api/src/main/java/my/pack/Api.java",
                         "package my.pack;\n" +
                         "public class Api {\n" +
                         "  public int method() { return 42; }\n" +
                         "}");

    createProjectSubFile("api/src/test/java/my/pack/ApiTest.java",
                         "package my.pack;\n" +
                         "public class ApiTest {}");

    implFile = createProjectSubFile("impl/src/main/java/my/pack/Impl.java", IMPL_JAVA);

    createProjectSubFile("impl/src/test/java/my/pack/ImplTest.java",
                         "package my.pack;\n" +
                         "import my.pack.ApiTest;\n" +
                         "public class ImplTest extends ApiTest {}");

    importProject("allprojects {\n" +
                  "  apply plugin: 'java'\n" +
                  "}\n" +
                  "\n" +
                  "dependencies {\n" +
                  "  compile project(':impl')\n" +
                  "}\n" +
                  "\n" +
                  "configure(project(':impl')) {\n" +
                  "  dependencies {\n" +
                  "    compile project(':api')\n" +
                  "  }\n" +
                  "}");

    assertModules("project", "project.main", "project.test",
                  "project.api", "project.api.main", "project.api.test",
                  "project.impl", "project.impl.main", "project.impl.test");
  }

  private void clearOutputs() {
    dirtyOutputRoots.clear();
    generatedFiles.clear();
  }

  @Nullable
  private String relativePath(@NotNull String path) {
    String basePath = PathUtil.toSystemIndependentName(getProjectPath());
    String filePath = PathUtil.toSystemIndependentName(path);
    return FileUtil.getRelativePath(basePath, filePath, '/');
  }
}
