// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.annotations.ApiStatus;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyReadContext;
import com.intellij.gradle.toolingExtension.impl.model.dependencyModel.DependencyWriteContext;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.ObjectFactory;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencySerialisationUtil.readDependency;
import static com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencySerialisationUtil.writeDependency;
import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class ExternalProjectSerializationService implements SerializationService<ExternalProject> {

  private static final String PROJECT_ID_FIELD = "id";
  private static final String PROJECT_PATH_FIELD = "path";
  private static final String PROJECT_IDENTITY_PATH_FIELD = "identityPath";
  private static final String PROJECT_NAME_FIELD = "name";
  private static final String PROJECT_Q_NAME_FIELD = "qName";
  private static final String PROJECT_DESCRIPTION_FIELD = "description";
  private static final String PROJECT_GROUP_FIELD = "group";
  private static final String PROJECT_VERSION_FIELD = "version";
  private static final String PROJECT_DIR_FIELD = "projectDir";
  private static final String PROJECT_BUILD_DIR_FIELD = "buildDir";
  private static final String PROJECT_BUILD_FILE_FIELD = "buildFile";
  private static final String PROJECT_TASKS_FIELD = "tasks";
  private static final String PROJECT_SOURCE_SET_MODEL_FIELD = "sourceSetModel";
  private static final String PROJECT_CHILD_PROJECTS_FIELD = "childProjects";

  private static final String SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD = "sourceCompatibility";
  private static final String SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD = "targetCompatibility";
  private static final String SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD = "taskArtifacts";
  private static final String SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD = "configurationArtifacts";
  private static final String SOURCE_SET_MODEL_SOURCE_SETS_FIELD = "sourceSets";
  private static final String SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD = "additionalArtifacts";

  private static final String SOURCE_SET_NAME_FIELD = "name";
  private static final String SOURCE_SET_SOURCE_COMPATIBILITY_FIELD = "sourceCompatibility";
  private static final String SOURCE_SET_TARGET_COMPATIBILITY_FIELD = "targetCompatibility";
  private static final String SOURCE_SET_IS_PREVIEW_FIELD = "isPreview";
  private static final String SOURCE_SET_ARTIFACTS_FIELD = "artifacts";
  private static final String SOURCE_SET_DEPENDENCIES_FIELD = "dependencies";
  private static final String SOURCE_SET_SOURCES_FIELD = "sources";
  private static final String SOURCE_SET_JAVA_TOOLCHAIN_FIELD = "javaToolchainHome";

  private static final String SOURCE_DIRECTORY_NAME_FIELD = "name";
  private static final String SOURCE_DIRECTORY_SRC_DIRS_FIELD = "srcDirs";
  private static final String SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD = "gradleOutputDirs";
  private static final String SOURCE_DIRECTORY_OUTPUT_DIR_FIELD = "outputDir";
  private static final String SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD = "inheritedCompilerOutput";
  private static final String SOURCE_DIRECTORY_PATTERNS_FIELD = "patterns";
  private static final String SOURCE_DIRECTORY_FILTERS_FIELD = "filters";

  private static final String FILTER_TYPE_FIELD = "filterType";
  private static final String FILTER_PROPERTIES_FIELD = "propertiesAsJsonMap";

  private static final String PATTERNS_INCLUDES_FIELD = "includes";
  private static final String PATTERNS_EXCLUDES_FIELD = "excludes";

  private static final String TASK_NAME_FIELD = "name";
  private static final String TASK_Q_NAME_FIELD = "qName";
  private static final String TASK_DESCRIPTION_FIELD = "description";
  private static final String TASK_GROUP_FIELD = "group";
  private static final String TASK_TYPE_FIELD = "type";
  private static final String TASK_IS_TEST_FIELD = "isTest";
  private static final String TASK_IS_JVM_TEST_FIELD = "isJvmTest";

  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(ExternalProject project, Class<? extends ExternalProject> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      writeProject(writer, myWriteContext, project);
    }
    return out.toByteArray();
  }

  @Override
  public ExternalProject read(byte[] object, Class<? extends ExternalProject> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return readProject(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends ExternalProject> getModelClass() {
    return ExternalProject.class;
  }

  private static void writeProject(IonWriter writer, WriteContext context, ExternalProject project) throws IOException {
    context.getProjectsCollector().add(project, (isAdded, objectId) -> {
      writer.stepIn(IonType.STRUCT);
      writeInt(writer, OBJECT_ID_FIELD, objectId);
      if (isAdded) {
        writeString(writer, PROJECT_ID_FIELD, project.getId());
        writeString(writer, PROJECT_PATH_FIELD, project.getPath());
        writeString(writer, PROJECT_IDENTITY_PATH_FIELD, project.getIdentityPath());
        writeString(writer, PROJECT_NAME_FIELD, project.getName());
        writeString(writer, PROJECT_Q_NAME_FIELD, project.getQName());
        writeString(writer, PROJECT_DESCRIPTION_FIELD, project.getDescription());
        writeString(writer, PROJECT_GROUP_FIELD, project.getGroup());
        writeString(writer, PROJECT_VERSION_FIELD, project.getVersion());
        writeString(writer, PROJECT_DIR_FIELD, project.getProjectDir().getPath());
        writeString(writer, PROJECT_BUILD_DIR_FIELD, project.getBuildDir().getPath());
        writeFile(writer, PROJECT_BUILD_FILE_FIELD, project.getBuildFile());
        writeTasks(writer, project);
        writeSourceSetModel(writer, context, project);
        writeChildProjects(writer, context, project);
      }
      writer.stepOut();
    });
  }

  private static void writeChildProjects(IonWriter writer, WriteContext context, ExternalProject project) throws IOException {
    writeCollection(writer, PROJECT_CHILD_PROJECTS_FIELD, project.getChildProjects().values(), it ->
      writeProject(writer, context, it)
    );
  }

  private static void writeSourceSetModel(IonWriter writer, WriteContext context, ExternalProject project) throws IOException {
    writer.setFieldName(PROJECT_SOURCE_SET_MODEL_FIELD);
    GradleSourceSetModel sourceSetModel = project.getSourceSetModel();
    writer.stepIn(IonType.STRUCT);
    writeString(writer, SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD, sourceSetModel.getSourceCompatibility());
    writeString(writer, SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD, sourceSetModel.getTargetCompatibility());
    writeFiles(writer, SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD, sourceSetModel.getTaskArtifacts());
    writeConfigurationArtifacts(writer, sourceSetModel);
    writeSourceSets(writer, context, sourceSetModel);
    writeFiles(writer, SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD, sourceSetModel.getAdditionalArtifacts());
    writer.stepOut();
  }

  private static void writeSourceSets(IonWriter writer, WriteContext context, GradleSourceSetModel sourceSetModel) throws IOException {
    writeCollection(writer, SOURCE_SET_MODEL_SOURCE_SETS_FIELD, sourceSetModel.getSourceSets().values(), it ->
      writeSourceSet(writer, context, it)
    );
  }

  private static void writeSourceSet(IonWriter writer, WriteContext context, ExternalSourceSet sourceSet) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, SOURCE_SET_NAME_FIELD, sourceSet.getName());
    writeString(writer, SOURCE_SET_SOURCE_COMPATIBILITY_FIELD, sourceSet.getSourceCompatibility());
    writeString(writer, SOURCE_SET_TARGET_COMPATIBILITY_FIELD, sourceSet.getTargetCompatibility());
    writeBoolean(writer, SOURCE_SET_IS_PREVIEW_FIELD, sourceSet.isPreview());
    writeFiles(writer, SOURCE_SET_ARTIFACTS_FIELD, sourceSet.getArtifacts());
    writeDependencies(writer, context, sourceSet);
    writeSourceDirectorySets(writer, sourceSet);
    writeFile(writer, SOURCE_SET_JAVA_TOOLCHAIN_FIELD, sourceSet.getJavaToolchainHome());
    writer.stepOut();
  }

  private static void writeSourceDirectorySets(IonWriter writer, ExternalSourceSet sourceSet) throws IOException {
    writeMap(writer, SOURCE_SET_SOURCES_FIELD, sourceSet.getSources(),
             it -> writeSourceDirectoryType(writer, it),
             it -> writeSourceDirectorySet(writer, it));
  }

  private static void writeSourceDirectoryType(IonWriter writer, IExternalSystemSourceType sourceType) throws IOException {
    writeString(writer, MAP_KEY_FIELD, ExternalSystemSourceType.from(sourceType).name());
  }

  private static void writeSourceDirectorySet(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writer.setFieldName(MAP_VALUE_FIELD);
    writer.stepIn(IonType.STRUCT);
    writeString(writer, SOURCE_DIRECTORY_NAME_FIELD, directorySet.getName());
    writeFiles(writer, SOURCE_DIRECTORY_SRC_DIRS_FIELD, directorySet.getSrcDirs());
    writeFiles(writer, SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD, directorySet.getGradleOutputDirs());
    writeFile(writer, SOURCE_DIRECTORY_OUTPUT_DIR_FIELD, directorySet.getOutputDir());
    writeBoolean(writer, SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD, directorySet.isCompilerOutputPathInherited());
    writePatterns(writer, directorySet);
    writeFilters(writer, directorySet);
    writer.stepOut();
  }

  private static void writeFilters(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writeCollection(writer, SOURCE_DIRECTORY_FILTERS_FIELD, directorySet.getFilters(), it ->
      writeFilter(writer, it)
    );
  }

  private static void writeFilter(IonWriter writer, ExternalFilter filter) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, FILTER_TYPE_FIELD, filter.getFilterType());
    writeString(writer, FILTER_PROPERTIES_FIELD, filter.getPropertiesAsJsonMap());
    writer.stepOut();
  }

  private static void writePatterns(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writer.setFieldName(SOURCE_DIRECTORY_PATTERNS_FIELD);
    FilePatternSet patterns = directorySet.getPatterns();
    writer.stepIn(IonType.STRUCT);
    writeStrings(writer, PATTERNS_INCLUDES_FIELD, patterns.getIncludes());
    writeStrings(writer, PATTERNS_EXCLUDES_FIELD, patterns.getExcludes());
    writer.stepOut();
  }

  private static void writeDependencies(IonWriter writer, WriteContext context, ExternalSourceSet sourceSet) throws IOException {
    writeCollection(writer, SOURCE_SET_DEPENDENCIES_FIELD, sourceSet.getDependencies(), it ->
      writeDependency(writer, context.myDependencyContext, it)
    );
  }

  private static void writeTasks(IonWriter writer, ExternalProject project) throws IOException {
    writeCollection(writer, PROJECT_TASKS_FIELD, project.getTasks().values(), it ->
      writeTask(writer, it)
    );
  }

  private static void writeTask(IonWriter writer, ExternalTask task) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, TASK_NAME_FIELD, task.getName());
    writeString(writer, TASK_Q_NAME_FIELD, task.getQName());
    writeString(writer, TASK_DESCRIPTION_FIELD, task.getDescription());
    writeString(writer, TASK_GROUP_FIELD, task.getGroup());
    writeString(writer, TASK_TYPE_FIELD, task.getType());
    writeBoolean(writer, TASK_IS_TEST_FIELD, task.isTest());
    writeBoolean(writer, TASK_IS_JVM_TEST_FIELD, task.isJvmTest());
    writer.stepOut();
  }

  private static void writeConfigurationArtifacts(IonWriter writer, GradleSourceSetModel sourceSetModel) throws IOException {
    writeMap(writer, SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD, sourceSetModel.getConfigurationArtifacts(),
             it -> writeString(writer, MAP_KEY_FIELD, it),
             it -> writeFiles(writer, MAP_VALUE_FIELD, it));
  }

  @Nullable
  private static DefaultExternalProject readProject(@NotNull IonReader reader, @NotNull ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    DefaultExternalProject project =
      context.getProjectsMap().computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new ObjectFactory<DefaultExternalProject>() {
        @Override
        public DefaultExternalProject newInstance() {
          return new DefaultExternalProject();
        }

        @Override
        public void fill(DefaultExternalProject externalProject) {
          externalProject.setExternalSystemId("GRADLE");
          externalProject.setId(assertNotNull(readString(reader, PROJECT_ID_FIELD)));
          externalProject.setPath(assertNotNull(readString(reader, PROJECT_PATH_FIELD)));
          externalProject.setIdentityPath(assertNotNull(readString(reader, PROJECT_IDENTITY_PATH_FIELD)));
          externalProject.setName(assertNotNull(readString(reader, PROJECT_NAME_FIELD)));
          externalProject.setQName(assertNotNull(readString(reader, PROJECT_Q_NAME_FIELD)));
          externalProject.setDescription(readString(reader, PROJECT_DESCRIPTION_FIELD));
          externalProject.setGroup(assertNotNull(readString(reader, PROJECT_GROUP_FIELD)));
          externalProject.setVersion(assertNotNull(readString(reader, PROJECT_VERSION_FIELD)));
          externalProject.setProjectDir(assertNotNull(readFile(reader, PROJECT_DIR_FIELD)));
          externalProject.setBuildDir(assertNotNull(readFile(reader, PROJECT_BUILD_DIR_FIELD)));
          externalProject.setBuildFile(assertNotNull(readFile(reader, PROJECT_BUILD_FILE_FIELD)));
          externalProject.setTasks(readTasks(reader));
          externalProject.setSourceSetModel(readSourceSetModel(reader, context));
          externalProject.setChildProjects(readChildProjects(reader, context));
        }
      });
    reader.stepOut();
    return project;
  }

  private static Map<String, DefaultExternalProject> readChildProjects(@NotNull IonReader reader, @NotNull ReadContext context) {
    return readList(reader, PROJECT_CHILD_PROJECTS_FIELD, () -> readProject(reader, context))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  private static Map<String, DefaultExternalTask> readTasks(IonReader reader) {
    return readList(reader, PROJECT_TASKS_FIELD, () -> readTask(reader))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  @Nullable
  private static DefaultExternalTask readTask(IonReader reader) {
    if (reader.next() == null) return null;
    reader.stepIn();
    DefaultExternalTask task = new DefaultExternalTask();
    task.setName(assertNotNull(readString(reader, TASK_NAME_FIELD)));
    task.setQName(assertNotNull(readString(reader, TASK_Q_NAME_FIELD)));
    task.setDescription(readString(reader, TASK_DESCRIPTION_FIELD));
    task.setGroup(readString(reader, TASK_GROUP_FIELD));
    task.setType(readString(reader, TASK_TYPE_FIELD));
    task.setTest(readBoolean(reader, TASK_IS_TEST_FIELD));
    task.setJvmTest(readBoolean(reader, TASK_IS_JVM_TEST_FIELD));
    reader.stepOut();
    return task;
  }

  private static @NotNull DefaultGradleSourceSetModel readSourceSetModel(IonReader reader, ReadContext context) {
    reader.next();
    assertFieldName(reader, PROJECT_SOURCE_SET_MODEL_FIELD);
    reader.stepIn();
    DefaultGradleSourceSetModel sourceSetModel = new DefaultGradleSourceSetModel();
    sourceSetModel.setSourceCompatibility(readString(reader, SOURCE_SET_MODEL_SOURCE_COMPATIBILITY_FIELD));
    sourceSetModel.setTargetCompatibility(readString(reader, SOURCE_SET_MODEL_TARGET_COMPATIBILITY_FIELD));
    sourceSetModel.setTaskArtifacts(readFileList(reader, SOURCE_SET_MODEL_TASK_ARTIFACTS_FIELD));
    sourceSetModel.setConfigurationArtifacts(readConfigurationArtifacts(reader));
    sourceSetModel.setSourceSets(readSourceSets(reader, context));
    sourceSetModel.setAdditionalArtifacts(readFileList(reader, SOURCE_SET_MODEL_ADDITIONAL_ARTIFACTS_FIELD));
    reader.stepOut();
    return sourceSetModel;
  }

  private static @NotNull Map<String, Set<File>> readConfigurationArtifacts(IonReader reader) {
    return readMap(reader, SOURCE_SET_MODEL_CONFIGURATION_ARTIFACTS_FIELD,
                   () -> readString(reader, MAP_KEY_FIELD),
                   () -> readFileSet(reader, MAP_VALUE_FIELD));
  }

  private static @NotNull Map<String, DefaultExternalSourceSet> readSourceSets(IonReader reader, ReadContext context) {
    return readList(reader, SOURCE_SET_MODEL_SOURCE_SETS_FIELD, () -> readSourceSet(reader, context))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  @Nullable
  private static DefaultExternalSourceSet readSourceSet(IonReader reader, ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    DefaultExternalSourceSet sourceSet = new DefaultExternalSourceSet();
    sourceSet.setName(readString(reader, SOURCE_SET_NAME_FIELD));
    sourceSet.setSourceCompatibility(readString(reader, SOURCE_SET_SOURCE_COMPATIBILITY_FIELD));
    sourceSet.setTargetCompatibility(readString(reader, SOURCE_SET_TARGET_COMPATIBILITY_FIELD));
    sourceSet.setPreview(readBoolean(reader, SOURCE_SET_IS_PREVIEW_FIELD));
    sourceSet.setArtifacts(readFileList(reader, SOURCE_SET_ARTIFACTS_FIELD));
    sourceSet.setDependencies(readDependencies(reader, context));
    sourceSet.setSources(readSourceDirectorySets(reader));
    sourceSet.setJavaToolchainHome(readFile(reader, SOURCE_SET_JAVA_TOOLCHAIN_FIELD));
    reader.stepOut();
    return sourceSet;
  }

  private static Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> readSourceDirectorySets(IonReader reader) {
    return readMap(reader, SOURCE_SET_SOURCES_FIELD,
                   () -> readSourceType(reader),
                   () -> readSourceDirectorySet(reader));
  }

  private static ExternalSystemSourceType readSourceType(IonReader reader) {
    return ExternalSystemSourceType.valueOf(assertNotNull(readString(reader, MAP_KEY_FIELD)));
  }

  private static DefaultExternalSourceDirectorySet readSourceDirectorySet(IonReader reader) {
    reader.next();
    assertFieldName(reader, MAP_VALUE_FIELD);
    reader.stepIn();
    DefaultExternalSourceDirectorySet directorySet = new DefaultExternalSourceDirectorySet();
    directorySet.setName(assertNotNull(readString(reader, SOURCE_DIRECTORY_NAME_FIELD)));
    directorySet.setSrcDirs(readFileSet(reader, SOURCE_DIRECTORY_SRC_DIRS_FIELD));
    directorySet.setGradleOutputDirs(readFileList(reader, SOURCE_DIRECTORY_GRADLE_OUTPUTS_FIELD));
    directorySet.setOutputDir(assertNotNull(readFile(reader, SOURCE_DIRECTORY_OUTPUT_DIR_FIELD)));
    directorySet.setCompilerOutputPathInherited(readBoolean(reader, SOURCE_DIRECTORY_INHERIT_COMPILER_OUTPUT_FIELD));
    directorySet.setPatterns(readPatterns(reader));
    directorySet.setFilters(readFilters(reader));
    reader.stepOut();
    return directorySet;
  }

  private static List<DefaultExternalFilter> readFilters(IonReader reader) {
    return readList(reader, SOURCE_DIRECTORY_FILTERS_FIELD, () ->
      readFilter(reader)
    );
  }

  @Nullable
  private static DefaultExternalFilter readFilter(IonReader reader) {
    if (reader.next() == null) return null;
    DefaultExternalFilter filter = new DefaultExternalFilter();
    reader.stepIn();
    filter.setFilterType(assertNotNull(readString(reader, FILTER_TYPE_FIELD)));
    filter.setPropertiesAsJsonMap(assertNotNull(readString(reader, FILTER_PROPERTIES_FIELD)));
    reader.stepOut();
    return filter;
  }

  private static FilePatternSet readPatterns(IonReader reader) {
    reader.next();
    assertFieldName(reader, SOURCE_DIRECTORY_PATTERNS_FIELD);
    reader.stepIn();
    FilePatternSetImpl patternSet = new FilePatternSetImpl();
    patternSet.setIncludes(readStringSet(reader, PATTERNS_INCLUDES_FIELD));
    patternSet.setExcludes(readStringSet(reader, PATTERNS_EXCLUDES_FIELD));
    reader.stepOut();
    return patternSet;
  }

  private static Collection<ExternalDependency> readDependencies(IonReader reader, ReadContext context) {
    return readList(reader, SOURCE_SET_DEPENDENCIES_FIELD, () ->
      readDependency(reader, context.myDependencyContext)
    );
  }

  private static class ReadContext {

    private final DependencyReadContext myDependencyContext = new DependencyReadContext();

    private final IntObjectMap<DefaultExternalProject> myProjectsMap = new IntObjectMap<>();

    public IntObjectMap<DefaultExternalProject> getProjectsMap() {
      return myProjectsMap;
    }
  }

  private static class WriteContext {

    private final DependencyWriteContext myDependencyContext = new DependencyWriteContext();

    private final ObjectCollector<ExternalProject, IOException> myProjectsCollector =
      new ObjectCollector<>();

    public ObjectCollector<ExternalProject, IOException> getProjectsCollector() {
      return myProjectsCollector;
    }
  }
}

