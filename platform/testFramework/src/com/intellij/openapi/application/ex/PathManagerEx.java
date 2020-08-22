// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.module.impl.ModulePath;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.Parameterized;
import com.intellij.testFramework.TestFrameworkUtil;
import gnu.trove.THashSet;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PathManagerEx {
  /**
   * All IDEA project files may be logically divided by the following criteria:
   * <ul>
   *   <li>files that are contained at {@code 'community'} directory;</li>
   *   <li>all other files;</li>
   * </ul>
   * <p/>
   * File location types implied by criteria mentioned above are enumerated here.
   */
  private enum FileSystemLocation {
    ULTIMATE, COMMUNITY
  }

  /**
   * Caches test data lookup strategy by class.
   */
  private static final ConcurrentMap<Class<?>, TestDataLookupStrategy> CLASS_STRATEGY_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
  private static Set<String> ourCommunityModules;

  private PathManagerEx() { }

  /**
   * Enumerates possible strategies of test data lookup.
   * <p/>
   * Check member-level javadoc for more details.
   */
  public enum TestDataLookupStrategy {
    /**
     * Stands for algorithm that retrieves {@code 'test data'} stored at the {@code 'ultimate'} project level assuming
     * that it's used from the test running in context of {@code 'ultimate'} project as well.
     * <p/>
     * Is assumed to be default strategy for all {@code 'ultimate'} tests.
     */
    ULTIMATE,

    /**
     * Stands for algorithm that retrieves {@code 'test data'} stored at the {@code 'community'} project level assuming
     * that it's used from the test running in context of {@code 'community'} project as well.
     * <p/>
     * Is assumed to be default strategy for all {@code 'community'} tests.
     */
    COMMUNITY,

    /**
     * Stands for algorithm that retrieves {@code 'test data'} stored at the {@code 'community'} project level assuming
     * that it's used from the test running in context of {@code 'ultimate'} project.
     */
    COMMUNITY_FROM_ULTIMATE
  }

  /**
   * It's assumed that test data location for both {@code community} and {@code ultimate} tests follows the same template:
   * <code>'<IDEA_HOME>/<RELATIVE_PATH>'</code>.
   * <p/>
   * {@code 'IDEA_HOME'} here stands for path to IDEA installation; {@code 'RELATIVE_PATH'} defines a path to
   * test data relative to IDEA installation path. That relative path may be different for {@code community}
   * and {@code ultimate} tests.
   * <p/>
   * This collection contains mappings from test group type to relative paths to use, i.e. it's possible to define more than one
   * relative path for the single test group. It's assumed that path definition algorithm iterates them and checks if
   * resulting absolute path points to existing directory. The one is returned in case of success; last path is returned otherwise.
   * <p/>
   * Hence, the order of relative paths for the single test group matters.
   */
  private static final List<Pair<TestDataLookupStrategy, String>> TEST_DATA_RELATIVE_PATHS = Arrays.asList(
    new Pair<>(TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE, FileUtil.toSystemDependentName("community/java/java-tests/testData")),
    new Pair<>(TestDataLookupStrategy.COMMUNITY, FileUtil.toSystemDependentName("java/java-tests/testData")),
    new Pair<>(TestDataLookupStrategy.ULTIMATE, "testData"));

  /**
   * Shorthand for calling {@link #getTestDataPath(TestDataLookupStrategy)} with
   * {@link #guessTestDataLookupStrategy() guessed} lookup strategy.
   *
   * @return    test data path with {@link #guessTestDataLookupStrategy() guessed} lookup strategy
   * @throws IllegalStateException    as defined by {@link #getTestDataPath(TestDataLookupStrategy)}
   */
  public static String getTestDataPath() throws IllegalStateException {
    TestDataLookupStrategy strategy = guessTestDataLookupStrategy();
    return getTestDataPath(strategy);
  }

  public static String getTestDataPath(String relativePath) throws IllegalStateException {
    return getTestDataPath() + FileUtil.toSystemDependentName(relativePath);
  }

  /**
   * Shorthand for calling {@link #getTestDataPath(TestDataLookupStrategy)} with strategy obtained via call to
   * {@link #determineLookupStrategy(Class)} with the given class.
   * <p/>
   * <b>Note:</b> this method receives explicit class argument in order to solve the following limitation - we analyze calling
   * stack trace in order to guess test data lookup strategy ({@link #guessTestDataLookupStrategyOnClassLocation()}). However,
   * there is a possible case that super-class method is called on sub-class object. Stack trace shows super-class then.
   * There is a possible situation that actual test is {@code 'ultimate'} but its abstract super-class is
   * {@code 'community'}, hence, test data lookup is performed incorrectly. So, this method should be called from abstract
   * base test class if its concrete sub-classes doesn't explicitly occur at stack trace.
   *
   *
   * @param testClass     target test class for which test data should be obtained
   * @return              base test data directory to use for the given test class
   * @throws IllegalStateException    as defined by {@link #getTestDataPath(TestDataLookupStrategy)}
   */
  public static String getTestDataPath(Class<?> testClass) throws IllegalStateException {
    TestDataLookupStrategy strategy = isLocatedInCommunity() ? TestDataLookupStrategy.COMMUNITY : determineLookupStrategy(testClass);
    return getTestDataPath(strategy);
  }

  /**
   * @return path to 'community' project home irrespective of current project
   */
  public static @NotNull String getCommunityHomePath() {
    return PathManager.getCommunityHomePath();
  }

  /**
   * @return path to 'community' project home if {@code testClass} is located in the community project and path to 'ultimate' project otherwise
   */
  public static String getHomePath(Class<?> testClass) {
    TestDataLookupStrategy strategy = isLocatedInCommunity() ? TestDataLookupStrategy.COMMUNITY : determineLookupStrategy(testClass);
    return strategy == TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE ? PathManager.getCommunityHomePath() : PathManager.getHomePath();
  }

  /**
   * Find file by its path relative to 'community' directory irrespective of current project
   * @param relativePath path to file relative to 'community' directory
   * @return file under the home directory of 'community' project
   */
  public static @NotNull File findFileUnderCommunityHome(@NotNull String relativePath) {
    return findFileByRelativePath(PathManager.getCommunityHomePath(), relativePath).toFile();
  }

  /**
   * Find file by its path relative to project home directory (the 'community' project if {@code testClass} is located
   * in the community project, and the 'ultimate' project otherwise)
   */
  public static @NotNull File findFileUnderProjectHome(@NotNull String relativePath, Class<? extends TestCase> testClass) {
    return findFileByRelativePath(getHomePath(testClass), relativePath).toFile();
  }

  private static @NotNull Path findFileByRelativePath(@NotNull String homePath, @NotNull String relativePath) {
    Path file = Paths.get(homePath, relativePath);
    if (!Files.exists(file)) {
      throw new IllegalArgumentException("Cannot find file '" + relativePath + "' under '" + homePath + "' directory");
    }
    return file;
  }

  private static boolean isLocatedInCommunity() {
    FileSystemLocation projectLocation = parseProjectLocation();
    return projectLocation == FileSystemLocation.COMMUNITY;
    // There is no other options then.
  }

  /**
   * Tries to return test data path for the given lookup strategy.
   *
   * @param strategy    lookup strategy to use
   * @return            test data path for the given strategy
   * @throws IllegalStateException    if it's not possible to find valid test data path for the given strategy
   */
  public static String getTestDataPath(TestDataLookupStrategy strategy) throws IllegalStateException {
    String homePath = PathManager.getHomePath();
    for (Pair<TestDataLookupStrategy, String> pair : TEST_DATA_RELATIVE_PATHS) {
      if (pair.first == strategy) {
        File candidate = new File(homePath, pair.second);
        if (candidate.isDirectory()) {
          return candidate.getPath();
        }
      }
    }
    throw new IllegalStateException(
      "Can't determine test data path for strategy '" + strategy + "' relative to home '" + homePath + "'." +
      " Configured mappings: " + TEST_DATA_RELATIVE_PATHS);
  }

  /**
   * Tries to guess test data lookup strategy for the current execution.
   *
   * @return    guessed lookup strategy for the current execution; defaults to {@link TestDataLookupStrategy#ULTIMATE}
   */
  public static TestDataLookupStrategy guessTestDataLookupStrategy() {
    TestDataLookupStrategy result = guessTestDataLookupStrategyOnClassLocation();
    if (result == null) {
      result = guessTestDataLookupStrategyOnDirectoryAvailability();
    }
    return result;
  }

  private static @Nullable TestDataLookupStrategy guessTestDataLookupStrategyOnClassLocation() {
    if (isLocatedInCommunity()) {
      return TestDataLookupStrategy.COMMUNITY;
    }

    // The general idea here is to find test class at the bottom of hierarchy and try to resolve test data lookup strategy
    // against it. Rationale is that there is a possible case that, say, 'ultimate' test class extends basic test class
    // that remains at 'community'. We want to perform the processing against 'ultimate' test class then.

    // About special abstract classes processing - there is a possible case that target test class extends abstract base
    // test class and call to this method is rooted from that parent. We need to resolve test data lookup against super
    // class then, hence, we keep track of found abstract test class as well and fallback to it if no non-abstract class is found.

    Class<?> testClass = null;
    Class<?> abstractTestClass = null;
    StackTraceElement[] stackTrace = new Exception().getStackTrace();
    for (StackTraceElement stackTraceElement : stackTrace) {
      String className = stackTraceElement.getClassName();
      Class<?> clazz = loadClass(className);
      if (clazz == null || TestCase.class == clazz || !isJUnitClass(clazz)) {
        continue;
      }

      if (determineLookupStrategy(clazz) == TestDataLookupStrategy.ULTIMATE) {
        return TestDataLookupStrategy.ULTIMATE;
      }
      if ((clazz.getModifiers() & Modifier.ABSTRACT) == 0) {
        testClass = clazz;
      }
      else {
        abstractTestClass = clazz;
      }
    }

    Class<?> classToUse = testClass == null ? abstractTestClass : testClass;
    return classToUse == null ? null : determineLookupStrategy(classToUse);
  }

  private static @Nullable Class<?> loadClass(String className) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

    Class<?> clazz = CLASS_CACHE.get(className);
    if (clazz != null) {
      return clazz;
    }

    ClassLoader definingClassLoader = PathManagerEx.class.getClassLoader();
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    for (ClassLoader classLoader : Arrays.asList(contextClassLoader, definingClassLoader, systemClassLoader)) {
      clazz = loadClass(className, classLoader);
      if (clazz != null) {
        CLASS_CACHE.put(className, clazz);
        return clazz;
      }
    }

    CLASS_CACHE.put(className, TestCase.class); //dummy
    return null;
  }

  private static @Nullable Class<?> loadClass(String className, ClassLoader classLoader) {
    try {
      return Class.forName(className, true, classLoader);
    }
    catch (NoClassDefFoundError | ClassNotFoundException e) {
      return null;
    }
  }

  @SuppressWarnings("TestOnlyProblems")
  private static boolean isJUnitClass(Class<?> clazz) {
    return TestCase.class.isAssignableFrom(clazz) || TestFrameworkUtil.isJUnit4TestClass(clazz, true) || Parameterized.class.isAssignableFrom(clazz);
  }

  private static TestDataLookupStrategy determineLookupStrategy(Class<?> clazz) {
    // Check if resulting strategy is already cached for the target class.
    TestDataLookupStrategy result = CLASS_STRATEGY_CACHE.get(clazz);
    if (result != null) return result;

    FileSystemLocation location = computeClassLocation(clazz);
    // We know that project location is ULTIMATE if control flow reaches this place.
    result = location == FileSystemLocation.COMMUNITY ? TestDataLookupStrategy.COMMUNITY_FROM_ULTIMATE : TestDataLookupStrategy.ULTIMATE;
    CLASS_STRATEGY_CACHE.put(clazz, result);
    return result;
  }

  public static void replaceLookupStrategy(Class<?> substitutor, Class<?>... initial) {
    TestDataLookupStrategy strategy = determineLookupStrategy(substitutor);
    CLASS_STRATEGY_CACHE.clear();
    for (Class<?> aClass : initial) CLASS_STRATEGY_CACHE.put(aClass, strategy);
  }

  private static FileSystemLocation computeClassLocation(Class<?> clazz) {
    String classRootPath = PathManager.getJarPathForClass(clazz);
    if (classRootPath == null) {
      throw new IllegalStateException("Cannot find root directory for " + clazz);
    }
    File root = new File(classRootPath);
    if (!root.exists()) {
      throw new IllegalStateException("Classes root " + root + " doesn't exist");
    }
    if (!root.isDirectory()) {
      //this means that clazz is located in a library, perhaps we should throw exception here
      return FileSystemLocation.ULTIMATE;
    }

    String moduleName = root.getName();
    String chunkPrefix = "ModuleChunk(";
    if (moduleName.startsWith(chunkPrefix)) {
      //todo[nik] this is temporary workaround to fix tests on TeamCity which compiles the whole modules cycle to a single output directory
      moduleName = StringUtil.trimStart(moduleName, chunkPrefix);
      moduleName = moduleName.substring(0, moduleName.indexOf(','));
    }
    return getCommunityModules().contains(moduleName) ? FileSystemLocation.COMMUNITY : FileSystemLocation.ULTIMATE;
  }

  private static synchronized Set<String> getCommunityModules() {
    if (ourCommunityModules != null) {
      return ourCommunityModules;
    }

    ourCommunityModules = new THashSet<>();
    File modulesXml = findFileUnderCommunityHome(Project.DIRECTORY_STORE_FOLDER + "/modules.xml");
    if (!modulesXml.exists()) {
      throw new IllegalStateException("Cannot obtain test data path: " + modulesXml.getAbsolutePath() + " not found");
    }

    try {
      Element element = JDomSerializationUtil.findComponent(JDOMUtil.load(modulesXml), JpsProjectLoader.MODULE_MANAGER_COMPONENT);
      assert element != null;
      for (ModulePath file : ModuleManagerImpl.getPathsToModuleFiles(element)) {
        ourCommunityModules.add(file.getModuleName());
      }
      return ourCommunityModules;
    }
    catch (JDOMException | IOException e) {
      throw new RuntimeException("Cannot read modules from " + modulesXml.getAbsolutePath(), e);
    }
  }

  /**
   * Allows to determine project type by its file system location.
   *
   * @return    project type implied by its file system location
   */
  private static FileSystemLocation parseProjectLocation() {
    return new File(PathManager.getHomePath(), "community/.idea").isDirectory() ? FileSystemLocation.ULTIMATE : FileSystemLocation.COMMUNITY;
  }

  /**
   * Tries to check test data lookup strategy by target test data directories availability.
   * <p/>
   * Such an approach has a drawback that it doesn't work correctly at number of scenarios, e.g. when
   * {@code 'community'} test is executed under {@code 'ultimate'} project.
   *
   * @return    test data lookup strategy based on target test data directories availability
   */
  private static TestDataLookupStrategy guessTestDataLookupStrategyOnDirectoryAvailability() {
    String homePath = PathManager.getHomePath();
    for (Pair<TestDataLookupStrategy, String> pair : TEST_DATA_RELATIVE_PATHS) {
      if (new File(homePath, pair.second).isDirectory()) {
        return pair.first;
      }
    }
    return TestDataLookupStrategy.ULTIMATE;
  }
}