// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.Stack;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.gradle.util.GradleVersion;
import org.gradle.wrapper.WrapperConfiguration;
import org.gradle.wrapper.WrapperExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.model.data.GradleProjectBuildScriptData;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Holds miscellaneous utility methods.
 */
public final class GradleUtil {
  private static final String LAST_USED_GRADLE_HOME_KEY = "last.used.gradle.home";

  private GradleUtil() { }

  /**
   * Allows to retrieve file chooser descriptor that filters gradle scripts.
   */
  public static @NotNull FileChooserDescriptor getGradleProjectFileChooserDescriptor() {
    return new FileChooserDescriptor(true, true, false, false, false, false)
      .withExtensionFilter(GradleBundle.message("gradle.filter.label"), GradleConstants.BUILD_FILE_EXTENSIONS);
  }

  public static boolean isGradleDefaultWrapperFilesExist(@Nullable String gradleProjectPath) {
    return getWrapperConfiguration(gradleProjectPath) != null;
  }

  /**
   * Tries to retrieve what settings should be used with gradle wrapper for the gradle project located at the given path.
   *
   * @param gradleProjectPath target gradle project config (*.gradle) path or config file's directory path.
   * @return gradle wrapper settings should be used with gradle wrapper for the gradle project located at the given path
   * if any; {@code null} otherwise
   */
  @Nullable
  public static WrapperConfiguration getWrapperConfiguration(@Nullable String gradleProjectPath) {
    Path wrapperPropertiesFile = findDefaultWrapperPropertiesFile(gradleProjectPath);
    if (wrapperPropertiesFile == null) {
      return null;
    }
    return readWrapperConfiguration(wrapperPropertiesFile);
  }

  public static @NotNull WrapperConfiguration generateGradleWrapperConfiguration(@NotNull GradleVersion gradleVersion) {
    WrapperConfiguration configuration = new WrapperConfiguration();
    URI distributionUri = getWrapperDistributionUri(gradleVersion);
    configuration.setDistribution(distributionUri);
    return configuration;
  }

  public static boolean writeWrapperConfiguration(@NotNull Path targetPath, @NotNull WrapperConfiguration wrapperConfiguration) {
    Properties wrapperProperties = new Properties();
    setFromWrapperConfiguration(wrapperConfiguration, wrapperProperties);
    return writeGradleProperties(wrapperProperties, targetPath);
  }

  public static @Nullable WrapperConfiguration readWrapperConfiguration(@NotNull Path wrapperPropertiesFile) {
    Properties props = readGradleProperties(wrapperPropertiesFile);
    if (props == null) {
      return null;
    }
    URI uri = parseDistributionUri(props, wrapperPropertiesFile);
    if (uri == null) {
      return null;
    }
    WrapperConfiguration wrapperConfiguration = new WrapperConfiguration();
    setToWrapperConfiguration(wrapperConfiguration, uri, props);
    return wrapperConfiguration;
  }

  public static void setToWrapperConfiguration(@NotNull WrapperConfiguration target,
                                               @NotNull URI disributionUri,
                                               @NotNull Properties source) {
    target.setDistribution(disributionUri);
    applyPropertyValue(source, WrapperExecutor.DISTRIBUTION_PATH_PROPERTY, target::setDistributionPath);
    applyPropertyValue(source, WrapperExecutor.DISTRIBUTION_BASE_PROPERTY, target::setDistributionBase);
    applyPropertyValue(source, WrapperExecutor.ZIP_STORE_PATH_PROPERTY, target::setZipPath);
    applyPropertyValue(source, WrapperExecutor.ZIP_STORE_BASE_PROPERTY, target::setZipBase);
  }

  public static void setFromWrapperConfiguration(@NotNull WrapperConfiguration source, @NotNull Properties target) {
    target.setProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, source.getDistribution().toString());
    target.setProperty(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY, source.getDistributionBase());
    target.setProperty(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY, source.getDistributionPath());
    target.setProperty(WrapperExecutor.ZIP_STORE_BASE_PROPERTY, source.getZipBase());
    target.setProperty(WrapperExecutor.ZIP_STORE_PATH_PROPERTY, source.getZipPath());
  }

  public static @Nullable Properties readGradleProperties(@NotNull Path propertiesFile) {
    try (BufferedReader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.ISO_8859_1)) {
      final Properties props = new Properties();
      props.load(reader);
      return props;
    }
    catch (NoSuchFileException ignored) {
    }
    catch (IOException e) {
      GradleLog.LOG.warn(
        String.format("I/O exception on reading gradle properties file at '%s'", propertiesFile.toAbsolutePath()), e);
    }
    return null;
  }

  private static boolean writeGradleProperties(@NotNull Properties properties, @NotNull Path propertiesFile) {
    try (BufferedWriter writer = Files.newBufferedWriter(propertiesFile, StandardCharsets.ISO_8859_1)) {
      properties.store(writer, null);
      return true;
    }
    catch (IOException e) {
      GradleLog.LOG.warn(
        String.format("I/O exception on writing Gradle properties into '%s'", propertiesFile.toAbsolutePath()), e);
    }
    return false;
  }

  private static void applyPropertyValue(@NotNull Properties props,
                                         @NotNull String propertyName,
                                         @NotNull Consumer<@NotNull String> valueConsumer) {
    String value = props.getProperty(propertyName);
    if (!StringUtil.isEmpty(value)) {
      valueConsumer.accept(value.trim());
    }
  }

  private static @Nullable URI parseDistributionUri(@NotNull Properties props, @NotNull Path propertiesFile) {
    String distributionUrl = props.getProperty(WrapperExecutor.DISTRIBUTION_URL_PROPERTY);
    if (StringUtil.isEmpty(distributionUrl)) {
      GradleLog.LOG.warn(String.format("Wrapper 'distributionUrl' property does not exist in file '%s'", propertiesFile.toAbsolutePath()));
      return null;
    }
    try {
      URI source = new URI(distributionUrl);
      return source.getScheme() != null ? source : propertiesFile.resolveSibling(source.getSchemeSpecificPart()).toUri();
    }
    catch (URISyntaxException e) {
      GradleLog.LOG.warn(
        String.format("Unable to resolve Gradle distribution path '%s' in file '%s'", distributionUrl, propertiesFile.toAbsolutePath()));
    }
    return null;
  }

  public static @NotNull URI getWrapperDistributionUri(@NotNull GradleVersion gradleVersion) {
    var gradleServicesUrl = GradleEnvironment.Urls.GRADLE_SERVICES_URL;
    var distributionSource = gradleVersion.isSnapshot() ? "distributions-snapshots" : "distributions";
    var gradleVersionString = gradleVersion.getVersion();
    try {
      return new URI(String.format("%s/%s/gradle-%s-bin.zip", gradleServicesUrl, distributionSource, gradleVersionString));
    }
    catch (URISyntaxException e) {
      throw new ExternalSystemException(e);
    }
  }

  /**
   * Allows to build file system path to the target gradle sub-project given the root project path.
   *
   * @param subProject      target sub-project which config path we're interested in
   * @param rootProjectPath path to root project's directory which contains 'build.gradle'
   * @return path to the given sub-project's directory which contains 'build.gradle'
   */
  @NotNull
  public static String getConfigPath(@NotNull GradleProject subProject, @NotNull String rootProjectPath) {
    try {
      GradleScript script = subProject.getBuildScript();
      if (script != null) {
        File file = script.getSourceFile();
        if (file != null) {
          if (!file.isDirectory()) {
            // The file points to 'build.gradle' at the moment but we keep it's parent dir path instead.
            file = file.getParentFile();
          }
          return ExternalSystemApiUtil.toCanonicalPath(file.getPath());
        }
      }
    }
    catch (Exception e) {
      // As said by gradle team: 'One thing I'm interested in is whether you have any thoughts about how the tooling API should
      // deal with missing details from the model - for example, when asking for details about the build scripts when using
      // a version of Gradle that does not supply that information. Currently, you'll get a `UnsupportedOperationException`
      // when you call the `getBuildScript()` method'.
      //
      // So, just ignore it and assume that the user didn't define any custom build file name.
    }
    File rootProjectParent = new File(rootProjectPath);
    StringBuilder buffer = new StringBuilder(FileUtil.toCanonicalPath(rootProjectParent.getAbsolutePath()));
    Stack<String> stack = new Stack<>();
    for (GradleProject p = subProject; p != null; p = p.getParent()) {
      stack.push(p.getName());
    }

    // pop root project
    stack.pop();
    while (!stack.isEmpty()) {
      buffer.append(ExternalSystemConstants.PATH_SEPARATOR).append(stack.pop());
    }
    return buffer.toString();
  }

  @NotNull
  public static String getLastUsedGradleHome() {
    return PropertiesComponent.getInstance().getValue(LAST_USED_GRADLE_HOME_KEY, "");
  }

  public static void storeLastUsedGradleHome(@Nullable String gradleHomePath) {
    PropertiesComponent.getInstance().setValue(LAST_USED_GRADLE_HOME_KEY, gradleHomePath, null);
  }

  @Nullable
  public static Path findDefaultWrapperPropertiesFile(@Nullable String gradleProjectPath) {
    if (gradleProjectPath == null) {
      return null;
    }
    Path file = Path.of(gradleProjectPath);

    // There is a possible case that given path points to a gradle script (*.gradle) but it's also possible that
    // it references script's directory. We want to provide flexibility here.
    Path gradleDir = Files.isRegularFile(file) ? file.resolveSibling("gradle") : file.resolve("gradle");
    if (!Files.isDirectory(gradleDir)) {
      return null;
    }

    Path wrapperDir = gradleDir.resolve("wrapper");
    if (!Files.isDirectory(wrapperDir)) {
      return null;
    }

    try (Stream<Path> pathsStream = Files.list(wrapperDir)) {
      List<Path> candidates = pathsStream
        .filter(path -> FileUtilRt.extensionEquals(path.getFileName().toString(), "properties") && Files.isRegularFile(path))
        .toList();

      if (candidates.isEmpty()) {
        GradleLog.LOG.warn("No *.properties file is found at the gradle wrapper directory " + wrapperDir);
        return null;
      }
      if (candidates.size() != 1) {
        GradleLog.LOG.warn(String.format("%d *.properties files instead of one have been found at the wrapper directory (%s): %s",
                                         candidates.size(), wrapperDir, StringUtil.join(candidates, ", ")
        ));
        return null;
      }
      return candidates.get(0);
    }
    catch (IOException e) {
      GradleLog.LOG.warn("Couldn't list gradle wrapper directory " + wrapperDir, e);
      return null;
    }
  }

  @NotNull
  public static String determineRootProject(@NotNull String subProjectPath) {
    final Path subProject = Paths.get(subProjectPath);
    Path candidate = subProject;
    try {
      while (candidate != null && candidate != candidate.getParent()) {
        if (containsGradleSettingsFile(candidate)) {
          return candidate.toString();
        }
        candidate = candidate.getParent();
      }
    }
    catch (IOException e) {
      GradleLog.LOG.warn("Failed to determine root Gradle project directory for [" + subProjectPath + "]", e);
    }
    return Files.isDirectory(subProject) ? subProjectPath : subProject.getParent().toString();
  }

  private static boolean containsGradleSettingsFile(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return false;
    }
    try (Stream<Path> stream = Files.walk(directory, 1)) {
      return stream
        .map(Path::getFileName)
        .filter(Objects::nonNull)
        .map(Path::toString)
        .anyMatch(name -> name.startsWith("settings.gradle"));
    }
  }

  /**
   * Finds real external module data by ide module
   * <p>
   * Module 'module' -> ModuleData 'module'
   * Module 'module.main' -> ModuleData 'module' instead of GradleSourceSetData 'module.main'
   * Module 'module.test' -> ModuleData 'module' instead of GradleSourceSetData 'module.test'
   */
  @ApiStatus.Experimental
  @Nullable
  public static DataNode<ModuleData> findGradleModuleData(@NotNull Module module) {
    String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
    if (projectPath == null) return null;
    Project project = module.getProject();
    return findGradleModuleData(project, projectPath);
  }

  @ApiStatus.Experimental
  @Nullable
  public static DataNode<ModuleData> findGradleModuleData(@NotNull Project project, @NotNull String projectPath) {
    return ExternalSystemApiUtil.findModuleNode(project, GradleConstants.SYSTEM_ID, projectPath);
  }

  public static @Nullable Module findGradleModule(@NotNull Project project, @NotNull String projectPath) {
    var moduleNode = ExternalSystemApiUtil.findModuleNode(project, GradleConstants.SYSTEM_ID, projectPath);
    if (moduleNode == null) return null;
    return findGradleModule(project, moduleNode.getData());
  }

  public static @Nullable Module findGradleModule(@NotNull Project project, @NotNull ProjectData projectData) {
    return findGradleModule(project, projectData.getLinkedExternalProjectPath());
  }

  public static @Nullable Module findGradleModule(@NotNull Project project, @NotNull ModuleData moduleData) {
    var modelsProvider = new IdeModelsProviderImpl(project);
    return modelsProvider.findIdeModule(moduleData);
  }

  @SuppressWarnings("unused") // used externally
  public static @NotNull GradleVersion getGradleVersion(Project project, PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      String filePath = virtualFile.getPath();
      return getGradleVersion(project, filePath);
    }
    return GradleVersion.current();
  }

  @SuppressWarnings("unused") // used externally
  public static @NotNull GradleVersion getGradleVersion(Project project, String filePath) {
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(GradleConstants.SYSTEM_ID);
    if (manager instanceof GradleManager gradleManager) {
      String externalProjectPath = gradleManager.getAffectedExternalProjectPath(filePath, project);
      if (externalProjectPath != null) {
        GradleSettings settings = GradleSettings.getInstance(project);
        GradleProjectSettings projectSettings = settings.getLinkedProjectSettings(externalProjectPath);
        if (projectSettings != null) {
          return projectSettings.resolveGradleVersion();
        }
      }
    }
    return GradleVersion.current();
  }

  @SuppressWarnings("unused") // used externally
  public static boolean isSupportedImplementationScope(@NotNull GradleVersion gradleVersion) {
    return GradleVersionUtil.isGradleAtLeast(gradleVersion, "3.4");
  }

  @Nullable
  public static VirtualFile getGradleBuildScriptSource(@NotNull Module module) {
    DataNode<? extends ModuleData> moduleData = findGradleModuleData(module);
    if (moduleData == null) return null;
    DataNode<GradleProjectBuildScriptData> dataNode = ExternalSystemApiUtil.find(moduleData, GradleProjectBuildScriptData.KEY);
    if (dataNode == null) return null;
    File data = dataNode.getData().getBuildScriptSource();
    if (data == null) return null;
    return VfsUtil.findFileByIoFile(data, true);
  }

  public static void excludeOutDir(@NotNull DataNode<ModuleData> ideModule, File ideaOutDir) {
    ContentRootData excludedContentRootData;
    DataNode<ContentRootData> contentRootDataDataNode = ExternalSystemApiUtil.find(ideModule, ProjectKeys.CONTENT_ROOT);
    if (contentRootDataDataNode == null || !isContentRootAncestor(contentRootDataDataNode.getData(), ideaOutDir)) {
      excludedContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, ideaOutDir.getPath());
      ideModule.createChild(ProjectKeys.CONTENT_ROOT, excludedContentRootData);
    }
    else {
      excludedContentRootData = contentRootDataDataNode.getData();
    }

    excludedContentRootData.storePath(ExternalSystemSourceType.EXCLUDED, ideaOutDir.getPath());
  }

  public static void unexcludeOutDir(@NotNull DataNode<ModuleData> ideModule, File ideaOutDir) {
    DataNode<ContentRootData> contentRootDataDataNode = ExternalSystemApiUtil.find(ideModule, ProjectKeys.CONTENT_ROOT);

    if (contentRootDataDataNode != null && isContentRootAncestor(contentRootDataDataNode.getData(), ideaOutDir)) {
          ContentRootData excludedContentRootData;
          excludedContentRootData = contentRootDataDataNode.getData();
          excludedContentRootData.getPaths(ExternalSystemSourceType.EXCLUDED).removeIf(sourceRoot -> {
            return sourceRoot.getPath().equals(ideaOutDir.getPath()); });
        }
  }

  private static boolean isContentRootAncestor(@NotNull ContentRootData data, @NotNull File ideaOutDir) {
    var canonicalIdeOutPath = FileUtil.toCanonicalPath(ideaOutDir.getPath());
    var canonicalRootPath = data.getRootPath();
    return FileUtil.isAncestor(canonicalRootPath, canonicalIdeOutPath, false);
  }
}
