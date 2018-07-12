// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework;

import com.intellij.ide.BootstrapClassLoaderUtil;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.WindowsCommandLineProcessor;
import com.intellij.idea.Main;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.lang.UrlClassLoader;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.openapi.application.PathManager.PROPERTY_CONFIG_PATH;
import static com.intellij.openapi.application.PathManager.PROPERTY_SYSTEM_PATH;
import static com.intellij.openapi.util.io.FileUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertNotNull;

public class IdeTestApplication {

  private static final Logger LOG = Logger.getInstance(IdeTestApplication.class);
  private static final String PROPERTY_IGNORE_CLASSPATH = "ignore.classpath";
  private static final String PROPERTY_ALLOW_BOOTSTRAP_RESOURCES = "idea.allow.bootstrap.resources";
  private static final String PROPERTY_ADDITIONAL_CLASSPATH = "idea.additional.classpath";
  private static final String CUSTOM_CONFIG_PATH= "CUSTOM_CONFIG_PATH";
  private static final String CUSTOM_SYSTEM_PATH= "CUSTOM_SYSTEM_PATH";

  private static IdeTestApplication ourInstance;

  protected ClassLoader myIdeClassLoader;

  @NotNull
  public ClassLoader getIdeClassLoader() {
    return myIdeClassLoader;
  }

  @NotNull
  public static File getFailedTestScreenshotDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "failures");
    ensureExists(dirPath);
    return dirPath;
  }

  @NotNull
  public static File getTestScreenshotDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "screenshots");
    ensureExists(dirPath);
    return dirPath;
  }

  @NotNull
  protected static File getGuiTestRootDirPath() throws IOException {
    String guiTestRootDirPathProperty = System.getProperty("gui.tests.root.dir.path");
    if (isNotEmpty(guiTestRootDirPathProperty)) {
      File rootDirPath = new File(guiTestRootDirPathProperty);
      if (rootDirPath.isDirectory()) {
        return rootDirPath;
      }
    }
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assertThat(homeDirPath).isNotEmpty();
    File rootDirPath = new File(homeDirPath, "gui-tests");
    ensureExists(rootDirPath);
    return rootDirPath;
  }


  @NotNull
  public static synchronized IdeTestApplication getInstance() throws Exception {
    String customConfigPath = GuiTestUtil.INSTANCE.getSystemPropertyOrEnvironmentVariable(CUSTOM_CONFIG_PATH);
    String customSystemPath = GuiTestUtil.INSTANCE.getSystemPropertyOrEnvironmentVariable(CUSTOM_SYSTEM_PATH);
    File configDirPath = null;
    boolean isDefaultConfig = true;
    if (StringUtil.isEmpty(customConfigPath)) {
      configDirPath = getConfigDirPath();
      System.setProperty(PROPERTY_CONFIG_PATH, configDirPath.getPath());
    } else {
      isDefaultConfig = false;
      File customConfigFile = new File(customConfigPath);
      System.setProperty(PROPERTY_CONFIG_PATH, customConfigFile.getPath());
    }

    if (! StringUtil.isEmpty(customSystemPath)) System.setProperty(PROPERTY_SYSTEM_PATH, Paths.get(customSystemPath).toFile().getPath());

    //Force Swing FileChooser on Mac (instead of native one) to be able to use FEST to drive it.
    System.setProperty("native.mac.file.chooser.enabled", "false");

    //We are using this property to skip testProjectLeak in _LastSuiteTests
    System.setProperty("idea.test.guimode", "true");

    if (!isLoaded()) {
      ourInstance = new IdeTestApplication();
      if (isDefaultConfig) recreateDirectory(configDirPath);

      File newProjectsRootDirPath = GuiTestUtil.INSTANCE.getProjectCreationDirPath();
      recreateDirectory(newProjectsRootDirPath);

      ClassLoader ideClassLoader = ourInstance.getIdeClassLoader();
      Class<?> clazz = ideClassLoader.loadClass(GuiTestUtil.class.getCanonicalName());
      method("waitForIdeToStart").in(clazz).invoke();
    }

    return ourInstance;
  }

  @NotNull
  private static File getConfigDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "config");
    ensureExists(dirPath);
    return dirPath;
  }

  private static void recreateDirectory(@NotNull File path) throws IOException {
    delete(path);
    ensureExists(path);
  }

  private IdeTestApplication() throws Exception {
    String[] args = EMPTY_STRING_ARRAY;

    LOG.assertTrue(ourInstance == null, "Only one instance allowed.");
    ourInstance = this;

    pluginManagerStart(args);

    myIdeClassLoader = createClassLoader();
    Thread.currentThread().setContextClassLoader(myIdeClassLoader);

    WindowsCommandLineProcessor.ourMirrorClass = Class.forName(WindowsCommandLineProcessor.class.getName(), true, myIdeClassLoader);

    Class<?> classUtilCoreClass = Class.forName("com.intellij.ide.ClassUtilCore", true, myIdeClassLoader);
    method("clearJarURLCache").in(classUtilCoreClass).invoke();

    Class<?> pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager", true, myIdeClassLoader);
    method("start").withParameterTypes(String.class, String.class, String[].class)
      .in(pluginManagerClass)
      .invoke("com.intellij.idea.MainImpl", "start", args);
  }

  // This method replaces BootstrapClassLoaderUtil.initClassLoader. The reason behind it is that when running UI tests the ClassLoader
  // containing the URLs for the plugin jars is loaded by a different ClassLoader and it gets ignored. The result is test failing because
  // classes like AndroidPlugin cannot be found.
  @NotNull
  private static ClassLoader createClassLoader() throws MalformedURLException {
    Collection<URL> classpath = new LinkedHashSet<>();
    addIdeaLibraries(classpath);
    addAdditionalClassPath(classpath);

    UrlClassLoader.Builder builder = UrlClassLoader.build()
      .urls(filterClassPath(new ArrayList<>(classpath)))
      //.parent(IdeTestApplication.class.getClassLoader())
      .allowLock(false)
      .usePersistentClasspathIndexForLocalClassDirectories();
    if (SystemProperties.getBooleanProperty(PROPERTY_ALLOW_BOOTSTRAP_RESOURCES, true)) {
      builder.allowBootstrapResources();
    }

    return builder.get();
  }

  private static void addIdeaLibraries(@NotNull Collection<URL> classpath) throws MalformedURLException {
    Class<BootstrapClassLoaderUtil> aClass = BootstrapClassLoaderUtil.class;
    String selfRoot = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    assertNotNull(selfRoot);

    URL selfRootUrl = new File(selfRoot).getAbsoluteFile().toURI().toURL();
    classpath.add(selfRootUrl);

    File libFolder = new File(PathManager.getLibPath());
    addLibraries(classpath, libFolder, selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ext"), selfRootUrl);
    addLibraries(classpath, new File(libFolder, "ant/lib"), selfRootUrl);
  }

  private static void addLibraries(@NotNull Collection<URL> classPath, @NotNull File fromDir, @NotNull URL selfRootUrl)
    throws MalformedURLException {
    for (File file : notNullize(fromDir.listFiles())) {
      if (isJarOrZip(file)) {
        URL url = file.toURI().toURL();
        if (!selfRootUrl.equals(url)) {
          classPath.add(url);
        }
      }
    }
  }

  private static void addAdditionalClassPath(@NotNull Collection<URL> classpath) throws MalformedURLException {
    StringTokenizer tokenizer = new StringTokenizer(System.getProperty(PROPERTY_ADDITIONAL_CLASSPATH, ""), File.pathSeparator, false);
    while (tokenizer.hasMoreTokens()) {
      String pathItem = tokenizer.nextToken();
      classpath.add(new File(pathItem).toURI().toURL());
    }
  }

  private static List<URL> filterClassPath(@NotNull List<URL> classpath) {
    String ignoreProperty = System.getProperty(PROPERTY_IGNORE_CLASSPATH);
    if (ignoreProperty != null) {
      Pattern pattern = Pattern.compile(ignoreProperty);
      for (Iterator<URL> i = classpath.iterator(); i.hasNext(); ) {
        String url = i.next().toExternalForm();
        if (pattern.matcher(url).matches()) {
          i.remove();
        }
      }
    }
    return classpath;
  }

  private static void pluginManagerStart(@NotNull String[] args) {
    // Duplicates what PluginManager#start does.
    Main.setFlags(args);
  }

  public void dispose() {
    disposeInstance();
  }

  public static synchronized void disposeInstance() {
    if (!isLoaded()) {
      return;
    }

    IdeEventQueue.getInstance().flushQueue();
    final Application application = ApplicationManager.getApplication();
    ((ApplicationImpl)application).exit(true, true, false);

    ourInstance = null;
  }

  public static synchronized boolean isLoaded() {
    return ourInstance != null;
  }

}
