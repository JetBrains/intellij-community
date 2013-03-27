package org.jetbrains.jps.android;

import com.android.SdkConstants;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.io.TestFileSystemItem;
import org.jetbrains.android.util.AndroidBuildTestingManager;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.*;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleExtensionImpl;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.cmdline.BuildMain;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.impl.JpsSimpleElementImpl;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.impl.JpsModuleSerializationDataExtensionImpl;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderTest extends JpsBuildTestCase {

  private static final String TEST_DATA_PATH = "/jps-plugin/testData/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(myProject).addResourcePattern("*.txt");
    myBuildParams.put(BuildMain.FORCE_MODEL_LOADING_PARAMETER.toString(), Boolean.TRUE.toString());
  }

  public void test1() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(ArrayUtil.EMPTY_STRING_ARRAY, executor, null).getFirst();
    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
        .dir("com")
        .dir("example")
        .dir("simple")
        .file("BuildConfig.class")
        .file("R.class")
        .end()
        .end()
        .end()
        .archive("module.apk")
        .file("META-INF")
        .file("res_apk_entry", "res_apk_entry_content")
        .file("classes.dex", "classes_dex_content"));
  }

  public void test2() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
      .dir("com")
      .dir("example")
      .dir("simple")
      .file("BuildConfig.class")
      .file("R.class")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content"));

    change(getProjectPath("src/com/example/simple/MyActivity.java"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/src/com/example/simple/MyActivity.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("res/layout/main.xml"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    change(getProjectPath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "</resources>");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_10");
    checkMakeUpToDate(executor);

    change(getProjectPath("res/values/strings.xml"),
           "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
           "<resources>\n" +
           "    <string name=\"app_name\">changed_string</string>\n" +
           "    <string name=\"new_string\">new_string</string>\n" +
           "</resources>");
    executor.setRClassContent("public static int change = 1;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.rename(new File(getProjectPath("res/drawable-hdpi/ic_launcher.png")),
                    new File(getProjectPath("res/drawable-hdpi/new_name.png")));
    executor.setRClassContent("public static int change = 2;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");
    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getProjectPath("res/drawable-hdpi/new_file.png")),
                         "new_file_png_content");
    executor.setRClassContent("public static int change = 3;");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/generated_sources/module/aapt/com/example/simple/R.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("libs/armeabi/mylib.so"), "mylib_content_changed");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_11");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    assertOutput(module, TestFileSystemItem.fs()
      .file("com")
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));

    checkMakeUpToDate(executor);

    change(getProjectPath("AndroidManifest.xml"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_6");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    delete(getProjectPath("AndroidManifest.xml"));
    copyToProject(getDefaultTestDataDirForCurrentTest() + "/changed_manifest.xml",
                  "root/AndroidManifest.xml");
    executor.clear();
    executor.setPackage("com.example.simple1");
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_7");
    assertCompiled(JavaBuilder.BUILDER_NAME,
                   "android/generated_sources/module/aapt/com/example/simple1/R.java",
                   "android/generated_sources/module/build_config/com/example/simple1/BuildConfig.java");

    checkMakeUpToDate(executor);

    change(getProjectPath("assets/myasset.txt"));
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_8");
    assertCompiled(JavaBuilder.BUILDER_NAME);

    checkMakeUpToDate(executor);

    FileUtil.writeToFile(new File(getProjectPath("assets/new_asset.png")),
                         "new_asset_content");
    executor.clear();
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_9");
    assertCompiled(JavaBuilder.BUILDER_NAME);
    checkMakeUpToDate(executor);

    assertOutput(module, TestFileSystemItem.fs()
      .dir("com")
      .dir("example")
      .dir("simple1")
      .file("BuildConfig.class")
      .file("R.class")
      .end()
      .dir("simple")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content")
      .dir("lib")
      .dir("armeabi")
      .file("mylib.so", "mylib_content_changed"));

    assertTrue(FileUtil.delete(new File(getProjectPath("libs/armeabi/mylib.so"))));
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_12");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("libs"))));
    rebuildAll();
    checkBuildLog(executor, "expected_log_13");
    checkMakeUpToDate(executor);
  }

  public void test3() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src", "resources"}, executor, null).getFirst();

    module.addSourceRoot(JpsPathUtil.pathToUrl(getProjectPath("tests")), JavaSourceRootType.TEST_SOURCE);

    final JpsLibrary lib1 = module.addModuleLibrary("lib1", JpsJavaLibraryType.INSTANCE);
    lib1.addRoot(getProjectPath("external_jar1.jar"), JpsOrderRootType.COMPILED);

    final JpsLibrary lib2 = module.addModuleLibrary("lib2", JpsJavaLibraryType.INSTANCE);
    lib2.addRoot(new File(getProjectPath("libs/external_jar2.jar")), JpsOrderRootType.COMPILED);

    module.getDependenciesList().addLibraryDependency(lib1);

    rebuildAll();
    checkBuildLog(executor, "expected_log");

    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .dir("com")
      .dir("example")
      .file("java_resource3.txt")
      .dir("simple")
      .file("BuildConfig.class")
      .file("R.class")
      .file("MyActivity.class")
      .end()
      .end()
      .end()
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("java_resource1.txt")
      .dir("com")
      .dir("example")
      .file("java_resource3.txt")
      .end()
      .end()
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));

    checkMakeUpToDate(executor);

    module.getDependenciesList().addLibraryDependency(lib2);

    executor.clear();
    makeAll().assertSuccessful();

    checkBuildLog(executor, "expected_log_1");

    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));

    checkMakeUpToDate(executor);

    change(getProjectPath("resources/com/example/java_resource3.txt"));

    executor.clear();
    makeAll().assertSuccessful();

    checkBuildLog(executor, "expected_log_2");
    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("external_jar1.jar"))));
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertOutput(module, TestFileSystemItem.fs()
      .file("java_resource1.txt")
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("resource_inside_jar2.txt")
      .file("java_resource1.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("src/java_resource1.txt"))));
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertOutput(module, TestFileSystemItem.fs()
      .file("com")
      .archive("module.apk")
      .file("resource_inside_jar1.txt")
      .file("resource_inside_jar2.txt")
      .file("com")
      .file("META-INF")
      .file("res_apk_entry", "res_apk_entry_content")
      .file("classes.dex", "classes_dex_content"));
    checkMakeUpToDate(executor);

    module.removeSourceRoot(JpsPathUtil.pathToUrl(getProjectPath("resources")), JavaSourceRootType.SOURCE);
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_5");
    checkMakeUpToDate(executor);
  }

  public void test4() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @Override
      protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
        if ("proguard_input_jar".equals(jarId)) {
          File tmpDir = null;

          try {
            tmpDir = FileUtil.createTempDirectory("proguard_input_jar_checking", "tmp");
            final File jar = new File(tmpDir, "file.jar");
            FileUtil.copy(new File(jarPath), jar);
            assertOutput(tmpDir.getPath(), TestFileSystemItem.fs()
              .archive("file.jar")
              .dir("com")
              .dir("example")
              .dir("simple")
              .file("BuildConfig.class")
              .file("R.class")
              .file("MyActivity.class")
              .file("MyClass.class"));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          finally {
            if (tmpDir != null) {
              FileUtil.delete(tmpDir);
            }
          }
        }
      }
    };
    final JpsModule androidModule = setUpSimpleAndroidStructure(new String[]{"src"}, executor, "android_module").getFirst();

    final String copiedSourceRoot = copyToProject(getDefaultTestDataDirForCurrentTest() +
                                                  "/project/java_module/src", "root/java_module/src");
    final JpsModule javaModule = addModule("java_module", copiedSourceRoot);

    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    androidModule.getDependenciesList().addModuleDependency(javaModule);
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    change(getProjectPath("src/com/example/simple/MyActivity.java"),
           "package com.example.simple;\n" +
           "import android.app.Activity;\n" +
           "import android.os.Bundle;\n" +
           "public class MyActivity extends Activity {\n" +
           "    @Override\n" +
           "    public void onCreate(Bundle savedInstanceState) {\n" +
           "        super.onCreate(savedInstanceState);\n" +
           "        final String s = MyClass.getMessage();\n" +
           "    }\n" +
           "}\n");
    makeAll();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/src/com/example/simple/MyActivity.java");
    checkMakeUpToDate(executor);

    change(getProjectPath("java_module/src/com/example/simple/MyClass.java"));
    makeAll();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "root/java_module/src/com/example/simple/MyClass.java");
    checkMakeUpToDate(executor);

    myBuildParams.put(AndroidCommonUtils.PROGUARD_CFG_PATH_OPTION, getProjectPath("proguard-project.txt"));
    myBuildParams.put(AndroidCommonUtils.INCLUDE_SYSTEM_PROGUARD_FILE_OPTION, Boolean.TRUE.toString());
    makeAll();
    checkBuildLog(executor, "expected_log_4");
    assertEquals(Collections.singleton("proguard_input_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);
  }

  public void test5() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", ArrayUtil.EMPTY_STRING_ARRAY, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.LIBRARY_PROJECT = true;

    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    appModule.getDependenciesList().addModuleDependency(libModule);
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtension appExtension = AndroidJpsUtil.getExtension(appModule);
    assert appExtension != null;
    final JpsAndroidModuleProperties appProps = ((JpsAndroidModuleExtensionImpl)appExtension).getProperties();
    appProps.myIncludeAssetsFromLibraries = true;

    makeAll();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);

    rebuildAll();
    checkBuildLog(executor, "expected_log_7");
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/assets/lib_asset.txt"));

    makeAll();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    change(getProjectPath("app/assets/app_asset.txt"));

    makeAll();
    checkBuildLog(executor, "expected_log_3");
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/res/values/strings.xml"));

    makeAll();
    checkBuildLog(executor, "expected_log_4");
    checkMakeUpToDate(executor);

    change(getProjectPath("app/res/values/strings.xml"));

    makeAll();
    checkBuildLog(executor, "expected_log_5");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/assets"))));

    makeAll();
    checkBuildLog(executor, "expected_log_6");
    checkMakeUpToDate(executor);
  }

  public void test6() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(ArrayUtil.EMPTY_STRING_ARRAY, executor, null).getFirst();
    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("res/drawable/ic_launcher1.png"))));
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void test7() throws Exception {
    final boolean[] class1Deleted = {false};

    final MyExecutor executor = new MyExecutor("com.example.simple") {
      @Override
      protected void doCheckJar(@NotNull String jarId, @NotNull String jarPath) {
        if ("library_package_jar".equals(jarId)) {
          File tmpDir = null;
          try {
            tmpDir = FileUtil.createTempDirectory("library_package_jar_checking", "tmp");
            final File jar = new File(tmpDir, "file.jar");
            FileUtil.copy(new File(jarPath), jar);
            TestFileSystemBuilder fs = TestFileSystemItem.fs()
              .archive("file.jar")
              .dir("lib")
              .file("MyLibClass.class");

            if (!class1Deleted[0]) {
              fs = fs.file("MyLibClass1.class");
            }
            assertOutput(tmpDir.getPath(), fs);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          finally {
            if (tmpDir != null) {
              FileUtil.delete(tmpDir);
            }
          }
        }
      }
    };
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);

    final JpsModule appModule = addAndroidModule("app", new String[]{"src"}, "app", "app", androidSdk).getFirst();
    final JpsModule libModule = addAndroidModule("lib", new String[]{"src"}, "lib", "lib", androidSdk).getFirst();

    final JpsAndroidModuleExtension libExtension = AndroidJpsUtil.getExtension(libModule);
    assert libExtension != null;
    final JpsAndroidModuleProperties libProps = ((JpsAndroidModuleExtensionImpl)libExtension).getProperties();
    libProps.LIBRARY_PROJECT = true;

    rebuildAll();
    checkBuildLog(executor, "expected_log");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    appModule.getDependenciesList().addModuleDependency(libModule);
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);

    change(getProjectPath("lib/src/lib/MyLibClass.java"));
    makeAll();
    checkBuildLog(executor, "expected_log_2");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/src/lib/MyLibClass1.java"))));
    class1Deleted[0] = true;
    makeAll();
    checkBuildLog(executor, "expected_log_2");
    assertEquals(Collections.singleton("library_package_jar"), executor.getCheckedJars());
    checkMakeUpToDate(executor);

    assertTrue(FileUtil.delete(new File(getProjectPath("lib/src/lib/MyLibClass.java"))));
    makeAll();
    checkBuildLog(executor, "expected_log_3");
    assertTrue(executor.getCheckedJars().isEmpty());
    checkMakeUpToDate(executor);
  }

  public void test8() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAll();
    checkMakeUpToDate(executor);

    myBuildParams.put(AndroidCommonUtils.RELEASE_BUILD_OPTION, Boolean.TRUE.toString());
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    myBuildParams.put(AndroidCommonUtils.RELEASE_BUILD_OPTION, Boolean.FALSE.toString());
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    myBuildParams.remove(AndroidCommonUtils.RELEASE_BUILD_OPTION);
    checkMakeUpToDate(executor);
  }

  public void testChangeDexSettings() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    rebuildAll();
    checkMakeUpToDate(executor);

    final JpsAndroidExtensionService service = JpsAndroidExtensionService.getInstance();
    final JpsAndroidDexCompilerConfiguration c = service.getDexCompilerConfiguration(myProject);
    assertNotNull(c);
    service.setDexCompilerConfiguration(myProject, c);

    c.setVmOptions("-Xms64m");
    makeAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    c.setMaxHeapSize(512);
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);

    c.setOptimize(false);
    makeAll();
    checkBuildLog(executor, "expected_log_2");
    checkMakeUpToDate(executor);
  }

  public void testFilteredResources() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null).getFirst();
    final JpsAndroidModuleProperties props = ((JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module)).getProperties();

    rebuildAll();
    checkMakeUpToDate(executor);

    props.USE_CUSTOM_APK_RESOURCE_FOLDER = true;
    props.CUSTOM_APK_RESOURCE_FOLDER = "/target/filtered-res";
    makeAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    change(getProjectPath("target/filtered-res/values/strings.xml"));
    makeAll();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void testCustomManifestPackage() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    final JpsModule module = setUpSimpleAndroidStructure(new String[]{"src"}, executor, null, "8").getFirst();
    rebuildAll();
    checkMakeUpToDate(executor);

    final JpsAndroidModuleExtensionImpl extension =
      (JpsAndroidModuleExtensionImpl)AndroidJpsUtil.getExtension(module);
    assert extension != null;
    final JpsAndroidModuleProperties props = extension.getProperties();

    props.CUSTOM_MANIFEST_PACKAGE = "dev";
    checkMakeUpToDate(executor);

    props.USE_CUSTOM_MANIFEST_PACKAGE = true;
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    props.CUSTOM_MANIFEST_PACKAGE = "dev1";
    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    checkMakeUpToDate(executor);
  }

  public void testGeneratedSources() throws Exception {
    final MyExecutor executor = new MyExecutor("com.example.simple");
    setUpSimpleAndroidStructure(new String[]{"src", "gen"}, executor, null).getFirst();

    rebuildAll();
    checkBuildLog(executor, "expected_log");
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/R.java"),
           AndroidCommonUtils.AUTOGENERATED_JAVA_FILE_HEADER + "\n\n" +
           "package com.example.simple;\n" +
           "public class R {}");

    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_1");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/copied_sources/module/com/example/simple/MyGeneratedClass.java");
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/R.java"));

    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_2");
    assertCompiled(JavaBuilder.BUILDER_NAME);
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/MyGeneratedClass.java"));

    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_3");
    assertCompiled(JavaBuilder.BUILDER_NAME, "android/copied_sources/module/com/example/simple/MyGeneratedClass.java");
    checkMakeUpToDate(executor);

    change(getProjectPath("gen/com/example/simple/MyGeneratedClass.java"),
           AndroidCommonUtils.AUTOGENERATED_JAVA_FILE_HEADER + "\n\n" +
           "package com.example.simple;\n" +
           "public class MyGeneratedClass {}");

    makeAll().assertSuccessful();
    checkBuildLog(executor, "expected_log_4");
    assertCompiled(JavaBuilder.BUILDER_NAME);
    checkMakeUpToDate(executor);
  }

  private void checkMakeUpToDate(MyExecutor executor) {
    executor.clear();
    makeAll().assertUpToDate();
    assertEquals("", executor.getLog());
  }

  private String getProjectPath(String relativePath) {
    return getAbsolutePath("root/" + relativePath);
  }

  private void checkBuildLog(MyExecutor executor, String expectedLogFile) throws IOException {
    final File file = findFindUnderProjectHome(getTestDataDirForCurrentTest(getTestName(true)) +
                                               "/" + expectedLogFile + ".txt");
    final String text = FileUtil.loadFile(file, true);
    assertEquals(AndroidBuildTestingCommandExecutor.normalizeExpectedLog(text, executor.getLog()),
                 AndroidBuildTestingCommandExecutor.normalizeLog(executor.getLog()));
  }

  private Pair<JpsModule, File> setUpSimpleAndroidStructure(String[] sourceRoots, MyExecutor executor, String contentRootDir) {
    return setUpSimpleAndroidStructure(sourceRoots, executor, contentRootDir, getTestName(true));
  }

  private Pair<JpsModule, File> setUpSimpleAndroidStructure(String[] sourceRoots,
                                                            MyExecutor executor,
                                                            String contentRootDir,
                                                            String testDirName) {
    final JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk = addJdkAndAndroidSdk();
    addPathPatterns(executor, androidSdk);
    return addAndroidModule("module", sourceRoots, contentRootDir, null, androidSdk, testDirName);
  }

  private void addPathPatterns(MyExecutor executor, JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> androidSdk) {
    final String tempDirectory = FileUtilRt.getTempDirectory();

    executor.addPathPrefix("PROJECT_DIR", getOrCreateProjectDir().getPath());
    executor.addPathPrefix("ANDROID_SDK_DIR", androidSdk.getHomePath());
    executor.addPathPrefix("DATA_STORAGE_ROOT", myDataStorageRoot.getPath());
    executor.addRegexPathPatternPrefix("AAPT_OUTPUT_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/android_apt_output\\d+tmp");
    executor.addRegexPathPatternPrefix("COMBINED_ASSETS_TMP", FileUtil.toSystemIndependentName(tempDirectory) +
                                                              "/android_combined_assets\\d+tmp");
    executor.addRegexPathPatternPrefix("CLASSPATH_TMP", FileUtil.toSystemIndependentName(tempDirectory) + "/classpath\\d+\\.tmp");
    executor.addRegexPathPattern("JAVA_PATH", ".*/java");
    executor.addRegexPathPattern("IDEA_RT_PATH", ".*/idea_rt.jar");
    executor.addRegexPathPattern("PROGUARD_INPUT_JAR", ".*/proguard_input\\S*\\.jar");
    AndroidBuildTestingManager.startBuildTesting(executor);
  }

  private Pair<JpsModule, File> addAndroidModule(String moduleName,
                                                 String[] sourceRoots,
                                                 String contentRootDir,
                                                 String dstContentRootDir,
                                                 JpsSdk<? extends JpsElement> androidSdk) {
    return addAndroidModule(moduleName, sourceRoots, contentRootDir,
                            dstContentRootDir, androidSdk, getTestName(true));
  }

  private Pair<JpsModule, File> addAndroidModule(String moduleName,
                                                 String[] sourceRoots,
                                                 String contentRootDir,
                                                 String dstContentRootDir,
                                                 JpsSdk<? extends JpsElement> androidSdk,
                                                 String testDirName) {
    final String testDataRoot = getTestDataDirForCurrentTest(testDirName);
    final String projectRoot = testDataRoot + "/project";
    final String moduleContentRoot = contentRootDir != null
                                     ? new File(projectRoot, contentRootDir).getPath()
                                     : projectRoot;
    final String dstRoot = dstContentRootDir != null ? "root/" + dstContentRootDir : "root";
    final String root = copyToProject(moduleContentRoot, dstRoot);
    final String outputPath = getAbsolutePath("out/production/" + moduleName);
    final String testOutputPath = getAbsolutePath("out/test/" + moduleName);

    final JpsModule module = addModule(moduleName, ArrayUtil.EMPTY_STRING_ARRAY,
                                       outputPath, testOutputPath, androidSdk);
    module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(root));

    for (String sourceRoot : sourceRoots) {
      final String sourceRootName = new File(sourceRoot).getName();
      final String copiedSourceRoot = copyToProject(moduleContentRoot + "/" + sourceRoot, dstRoot + "/" + sourceRootName);
      module.addSourceRoot(JpsPathUtil.pathToUrl(copiedSourceRoot), JavaSourceRootType.SOURCE);
    }
    final JpsAndroidModuleProperties properties = new JpsAndroidModuleProperties();

    properties.MANIFEST_FILE_RELATIVE_PATH = "/AndroidManifest.xml";
    properties.RES_FOLDER_RELATIVE_PATH = "/res";
    properties.ASSETS_FOLDER_RELATIVE_PATH = "/assets";
    properties.LIBS_FOLDER_RELATIVE_PATH = "/libs";
    properties.GEN_FOLDER_RELATIVE_PATH_APT = "/gen";
    properties.GEN_FOLDER_RELATIVE_PATH_AIDL = "/gen";
    properties.PACK_TEST_CODE = false;

    module.getContainer().setChild(JpsModuleSerializationDataExtensionImpl.ROLE,
                                   new JpsModuleSerializationDataExtensionImpl(new File(root)));
    module.getContainer().setChild(JpsAndroidModuleExtensionImpl.KIND, new JpsAndroidModuleExtensionImpl(properties));
    return Pair.create(module, new File(root));
  }

  private String getDefaultTestDataDirForCurrentTest() {
    return getTestDataDirForCurrentTest(getTestName(true));
  }

  private static String getTestDataDirForCurrentTest(String testDirName) {
    return TEST_DATA_PATH + testDirName;
  }

  private JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>> addJdkAndAndroidSdk() {
    final String jdkName = "java_sdk";
    addJdk(jdkName);
    final JpsAndroidSdkProperties properties = new JpsAndroidSdkProperties("android-17", jdkName);
    final String sdkPath = getAndroidHomePath() + "/testData/sdk1.5";

    final JpsTypedLibrary<JpsSdk<JpsSimpleElement<JpsAndroidSdkProperties>>> library =
      myModel.getGlobal().addSdk("android_sdk", sdkPath, "", JpsAndroidSdkType.INSTANCE,
                                 new JpsSimpleElementImpl<JpsAndroidSdkProperties>(properties));
    library.addRoot(new File(sdkPath + "/platforms/android-1.5/android.jar"), JpsOrderRootType.COMPILED);
    //library.addRoot(new File(getAndroidHomePath() + "/testData/android.jar"), JpsOrderRootType.COMPILED);
    return library.getProperties();
  }

  @Override
  protected File findFindUnderProjectHome(String relativePath) {
    final String homePath = getAndroidHomePath();
    final File file = new File(homePath, FileUtil.toSystemDependentName(relativePath));

    if (!file.exists()) {
      throw new IllegalArgumentException("Cannot find file '" + relativePath + "' under '" + homePath + "' directory");
    }
    return file;
  }

  @NotNull
  private static String getAndroidHomePath() {
    final String androidHomePath = System.getProperty("android.home.path");

    if (androidHomePath != null) {
      return androidHomePath;
    }
    return new File(PathManager.getHomePath(), "community/plugins/android").getPath();
  }

  private static void createTextFile(@NotNull String path, @NotNull String text) throws IOException {
    final File f = new File(path);
    final File parent = f.getParentFile();

    if (!parent.exists() && !parent.mkdirs()) {
      throw new IOException("Cannot create directory " + parent.getPath());
    }
    final OutputStream stream = new FileOutputStream(f);

    try {
      stream.write(text.getBytes(Charset.defaultCharset()));
    }
    finally {
      stream.close();
    }
  }

  @SuppressWarnings("SSBasedInspection")
  private static class MyExecutor extends AndroidBuildTestingCommandExecutor {

    private String myRClassContent = "";

    private String myPackage;

    public MyExecutor(String aPackage) {
      myPackage = aPackage;
    }

    void setPackage(String aPackage) {
      myPackage = aPackage;
    }

    void setRClassContent(String RClassContent) {
      myRClassContent = RClassContent;
    }

    @NotNull
    @Override
    protected Process doCreateProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment)
      throws Exception {
      final int idx = ArrayUtilRt.find(args, "org.jetbrains.android.compiler.tools.AndroidDxRunner");

      if (idx >= 0) {
        final String outputPath = args[idx + 2];
        createTextFile(outputPath, "classes_dex_content");
        return new MyProcess(0, "", "");
      }

      if (args[0].endsWith(SdkConstants.FN_AAPT)) {
        if ("package".equals(args[1])) {
          if ("-m".equals(args[2])) {
            final String outputDir = args[4];
            createTextFile(outputDir + "/" + myPackage.replace('.', '/') + "/R.java",
                           "package " + myPackage + ";\n" +
                           "public class R {" + myRClassContent + "}");

            if ("-G".equals(args[args.length - 2])) {
              createTextFile(args[args.length - 1], "generated_proguard_file_by_aapt");
            }
          }
          else if ("-S".equals(args[2])) {
            final String outputPath = args[args.length - 1];
            final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputPath)));

            try {
              appendEntry(zos, "res_apk_entry", "res_apk_entry_content".getBytes());
            }
            finally {
              zos.close();
            }
          }
        }
        else if ("crunch".equals(args[1])) {
          final String outputDir = args[args.length - 1];
          createTextFile(outputDir + "/drawable/crunch_output1.png", "crunch_output1_content");
          createTextFile(outputDir + "/drawable/crunch_output2.png", "crunch_output2_content");
        }
      }
      return new MyProcess(0, "", "");
    }

    private static void appendEntry(ZipOutputStream zos, String name, byte[] content) throws Exception {
      final ZipEntry e = new ZipEntry(name);
      e.setMethod(ZipEntry.STORED);
      e.setSize(content.length);
      CRC32 crc = new CRC32();
      crc.update(content);
      e.setCrc(crc.getValue());
      zos.putNextEntry(e);
      zos.write(content, 0, content.length);
      zos.closeEntry();
    }
  }
}
