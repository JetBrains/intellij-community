// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.compiler;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.task.ProjectTaskContext;
import com.intellij.task.ProjectTaskListener;
import com.intellij.task.ProjectTaskManager;
import com.intellij.util.PathUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class GradleImprovedHotswapDetectionTest extends GradleDelegatedBuildTestCase {
  @Language("Java")
  private static final String APP_JAVA =
    """
      package my.pack;
      public class App {
        public int method() { return 42; }
      }""";

  // App.java with a new method added
  @Language("Java")
  private static final String APP_JAVA_WITH_NEW_METHOD =
    """
      package my.pack;
      public class App extends Impl {
        public int method() { return 42; }
        public int methodX() { return 100_000; }
      }""";

  // App.java with new added NewInnerClass inner class
  @Language("Java")
  private static final String APP_JAVA_WITH_INNER_CLASS =
    """
      package my.pack;
      public class App extends Impl {
        public int method() { return 42; }
        public static class NewInnerClass {
          public void doNothing() {}  }}""";

  // the NewInnerClass method changed from 'doNothing' to 'doSomething'
  @Language("Java")
  private static final String APP_JAVA_WITH_MODIFIED_INNER_CLASS =
    """
      package my.pack;
      public class App extends Impl {
        public int method() { return 42; }
        public static class NewInnerClass {
          public boolean doSomething() { return true; }
        }
      }""";

  @Language("Java")
  private static final String IMPL_JAVA =
    """
      package my.pack;
      import my.pack.Api;
      public class Impl extends Api {}""";

  @Language("Java")
  private static final String IMPL_JAVA_WITH_NEW_METHOD =
    """
      package my.pack;
      import my.pack.Api;
      public class Impl extends Api {
        public void newImplMethod() {}
      }""";

  private String mainRoot;
  private String testRoot;
  private String apiMainRoot;
  private String apiTestRoot;
  private String implMainRoot;
  private String implTestRoot;
  private String apiJar;

  private VirtualFile appFile;
  private VirtualFile implFile;
  private final List<String> dirtyOutputRoots = new ArrayList<>();
  private final Map<String, Collection<String>> generatedFiles = new HashMap<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();

    mainRoot = "build/classes/java/main";
    testRoot = "build/classes/java/test";
    apiMainRoot = "api/build/classes/java/main";
    apiTestRoot = "api/build/classes/java/test";
    implMainRoot = "impl/build/classes/java/main";
    implTestRoot = "impl/build/classes/java/test";
    apiJar = "api/build/libs/api.jar";

    clearOutputs();
    Registry.get("gradle.improved.hotswap.detection").setValue(true, getTestRootDisposable());
    createProject();
    subscribeToProject();
  }

  @Test
  public void testBuildMainProject() {
    compileModules("project.main");

    List<String> expected = new ArrayList<>(asList(apiJar));

    if (isGradleAtLeast("7.1")) {
      expected.addAll(asList("build/tmp/compileJava/previous-compilation-data.bin",
                             "api/build/tmp/compileJava/previous-compilation-data.bin",
                             "impl/build/tmp/compileJava/previous-compilation-data.bin"));
    }

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrderElementsOf(expected);

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

    List<String> expected = new ArrayList<>(asList(apiJar));

    if (isGradleAtLeast("7.1")) {
      expected.addAll(asList("build/tmp/compileJava/previous-compilation-data.bin",
                             "build/tmp/compileTestJava/previous-compilation-data.bin",
                             "api/build/tmp/compileJava/previous-compilation-data.bin",
                             "impl/build/tmp/compileJava/previous-compilation-data.bin"));
    }

    assertThat(dirtyOutputRoots).containsExactlyInAnyOrderElementsOf(expected);

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

    if (isGradleAtLeast("7.1")) {
      assertThat(dirtyOutputRoots).as("Dirty output roots").containsExactlyInAnyOrder("build/tmp/compileJava/previous-compilation-data.bin");
    } else {
      assertThat(dirtyOutputRoots).as("Dirty output roots").isEmpty();
    }

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

    List<String> expected = new ArrayList<>();

    if (isGradleAtLeast("7.1")) {
      expected.add("impl/build/tmp/compileJava/previous-compilation-data.bin");
    }

    assertThat(dirtyOutputRoots).as("Dirty output roots").containsExactlyInAnyOrderElementsOf(expected);

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

    if (isGradleAtLeast("7.1")) {
      assertThat(dirtyOutputRoots).as("Dirty output roots").containsExactlyInAnyOrder("build/tmp/compileTestJava/previous-compilation-data.bin");
    } else {
      assertThat(dirtyOutputRoots).as("Dirty output roots").isEmpty();
    }
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

    setFileContent(appFile, """
      package my.pack;
      public class App {
        public int method() { return 42; }
        public int methodX() { return 42; }
      }""", false);

    clearOutputs();
    compileModules("project.main");

    // revert file to previous value
    setFileContent(appFile, APP_JAVA, false);

    clearOutputs();
    compileModules("project.main");

    if (isGradleAtLeast("7.1")) {
      assertThat(dirtyOutputRoots).as("Dirty output roots").containsExactlyInAnyOrder("build/tmp/compileJava/previous-compilation-data.bin");
    } else {
      assertThat(dirtyOutputRoots).as("Dirty output roots").isEmpty();
    }
    assertThat(generatedFiles).containsOnly(Map.entry(mainRoot, Set.of("my/pack/App.class")));
  }

  @Test
  public void testBuildProjectWithResources() throws IOException {
    compileModules("project.main");
    createProjectSubFile("src/main/resources/runtime.properties",
                         "resourceString=foobar");

    clearOutputs();
    compileModules("project.main");

    assertThat(dirtyOutputRoots).as("Dirty output roots").isEmpty();
    assertThat(generatedFiles).containsOnly(Map.entry("build/resources/main", Set.of("runtime.properties")));
  }

  private void subscribeToProject() {
    MessageBusConnection connection = getMyProject().getMessageBus().connect(getTestRootDisposable());
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
                         """
                           package my.pack;
                           public class Other {
                             public String method() { return "foo"; }
                           }""");

    createProjectSubFile("src/test/java/my/pack/AppTest.java",
                         """
                           package my.pack;
                           public class AppTest {
                             public void test() { new App().method(); }
                           }""");

    createProjectSubFile("api/src/main/java/my/pack/Api.java",
                         """
                           package my.pack;
                           public class Api {
                             public int method() { return 42; }
                           }""");

    createProjectSubFile("api/src/test/java/my/pack/ApiTest.java",
                         "package my.pack;\n" +
                         "public class ApiTest {}");

    implFile = createProjectSubFile("impl/src/main/java/my/pack/Impl.java", IMPL_JAVA);

    createProjectSubFile("impl/src/test/java/my/pack/ImplTest.java",
                         """
                           package my.pack;
                           import my.pack.ApiTest;
                           public class ImplTest extends ApiTest {}""");

    importProject(script(it -> {
      it.allprojects(TestGradleBuildScriptBuilder::withJavaPlugin)
        .addImplementationDependency(it.project(":impl"))
        .project(":impl", p -> {
          p
            .withJavaLibraryPlugin()
            .addApiDependency(p.project(":api"));
        });
    }));

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
