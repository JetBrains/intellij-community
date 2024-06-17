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
        writeString(writer, "id", project.getId());
        writeString(writer, "path", project.getPath());
        writeString(writer, "identityPath", project.getIdentityPath());
        writeString(writer, "name", project.getName());
        writeString(writer, "qName", project.getQName());
        writeString(writer, "description", project.getDescription());
        writeString(writer, "group", project.getGroup());
        writeString(writer, "version", project.getVersion());
        writeString(writer, "projectDir", project.getProjectDir().getPath());
        writeString(writer, "buildDir", project.getBuildDir().getPath());
        writeFile(writer, "buildFile", project.getBuildFile());
        writeTasks(writer, project);
        writeSourceSetModel(writer, context, project);
        writeChildProjects(writer, context, project);
      }
      writer.stepOut();
    });
  }

  private static void writeChildProjects(IonWriter writer, WriteContext context, ExternalProject project) throws IOException {
    writeCollection(writer, "childProjects", project.getChildProjects().values(), it ->
      writeProject(writer, context, it)
    );
  }

  private static void writeSourceSetModel(IonWriter writer, WriteContext context, ExternalProject project) throws IOException {
    writer.setFieldName("sourceSetModel");
    GradleSourceSetModel sourceSetModel = project.getSourceSetModel();
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "sourceCompatibility", sourceSetModel.getSourceCompatibility());
    writeString(writer, "targetCompatibility", sourceSetModel.getTargetCompatibility());
    writeFiles(writer, "taskArtifacts", sourceSetModel.getTaskArtifacts());
    writeConfigurationArtifacts(writer, sourceSetModel);
    writeSourceSets(writer, context, sourceSetModel);
    writeFiles(writer, "additionalArtifacts", sourceSetModel.getAdditionalArtifacts());
    writer.stepOut();
  }

  private static void writeSourceSets(IonWriter writer, WriteContext context, GradleSourceSetModel sourceSetModel) throws IOException {
    writeCollection(writer, "sourceSets", sourceSetModel.getSourceSets().values(), it ->
      writeSourceSet(writer, context, it)
    );
  }

  private static void writeSourceSet(IonWriter writer, WriteContext context, ExternalSourceSet sourceSet) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "name", sourceSet.getName());
    writeString(writer, "sourceCompatibility", sourceSet.getSourceCompatibility());
    writeString(writer, "targetCompatibility", sourceSet.getTargetCompatibility());
    writeBoolean(writer, "isPreview", sourceSet.isPreview());
    writeFiles(writer, "artifacts", sourceSet.getArtifacts());
    writeDependencies(writer, context, sourceSet);
    writeSourceDirectorySets(writer, sourceSet);
    writeFile(writer, "javaToolchainHome", sourceSet.getJavaToolchainHome());
    writer.stepOut();
  }

  private static void writeSourceDirectorySets(IonWriter writer, ExternalSourceSet sourceSet) throws IOException {
    writeMap(writer, "sources", sourceSet.getSources(),
             it -> writeSourceDirectoryType(writer, it),
             it -> writeSourceDirectorySet(writer, it)
    );
  }

  private static void writeSourceDirectoryType(IonWriter writer, IExternalSystemSourceType sourceType) throws IOException {
    writer.writeString(ExternalSystemSourceType.from(sourceType).name());
  }

  private static void writeSourceDirectorySet(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "name", directorySet.getName());
    writeFiles(writer, "srcDirs", directorySet.getSrcDirs());
    writeFiles(writer, "gradleOutputDirs", directorySet.getGradleOutputDirs());
    writeFile(writer, "outputDir", directorySet.getOutputDir());
    writeBoolean(writer, "inheritedCompilerOutput", directorySet.isCompilerOutputPathInherited());
    writePatterns(writer, directorySet);
    writeFilters(writer, directorySet);
    writer.stepOut();
  }

  private static void writeFilters(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writeCollection(writer, "filters", directorySet.getFilters(), it ->
      writeFilter(writer, it)
    );
  }

  private static void writeFilter(IonWriter writer, ExternalFilter filter) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "filterType", filter.getFilterType());
    writeString(writer, "propertiesAsJsonMap", filter.getPropertiesAsJsonMap());
    writer.stepOut();
  }

  private static void writePatterns(IonWriter writer, ExternalSourceDirectorySet directorySet) throws IOException {
    writer.setFieldName("patterns");
    FilePatternSet patterns = directorySet.getPatterns();
    writer.stepIn(IonType.STRUCT);
    writeStrings(writer, "includes", patterns.getIncludes());
    writeStrings(writer, "excludes", patterns.getExcludes());
    writer.stepOut();
  }

  private static void writeDependencies(IonWriter writer, WriteContext context, ExternalSourceSet sourceSet) throws IOException {
    writeCollection(writer, "dependencies", sourceSet.getDependencies(), it ->
      writeDependency(writer, context.myDependencyContext, it)
    );
  }

  private static void writeTasks(IonWriter writer, ExternalProject project) throws IOException {
    writeCollection(writer, "tasks", project.getTasks().values(), it ->
      writeTask(writer, it)
    );
  }

  private static void writeTask(IonWriter writer, ExternalTask task) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "name", task.getName());
    writeString(writer, "qName", task.getQName());
    writeString(writer, "description", task.getDescription());
    writeString(writer, "group", task.getGroup());
    writeString(writer, "type", task.getType());
    writeBoolean(writer, "isTest", task.isTest());
    writeBoolean(writer, "isJvmTest", task.isJvmTest());
    writer.stepOut();
  }

  private static void writeConfigurationArtifacts(IonWriter writer, GradleSourceSetModel sourceSetModel) throws IOException {
    writeMap(writer, "configurationArtifacts", sourceSetModel.getConfigurationArtifacts(),
             it -> writer.writeString(it),
             it -> writeFiles(writer, "value", it)
    );
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
          externalProject.setId(assertNotNull(readString(reader, "id")));
          externalProject.setPath(assertNotNull(readString(reader, "path")));
          externalProject.setIdentityPath(assertNotNull(readString(reader, "identityPath")));
          externalProject.setName(assertNotNull(readString(reader, "name")));
          externalProject.setQName(assertNotNull(readString(reader, "qName")));
          externalProject.setDescription(readString(reader, "description"));
          externalProject.setGroup(assertNotNull(readString(reader, "group")));
          externalProject.setVersion(assertNotNull(readString(reader, "version")));
          externalProject.setProjectDir(assertNotNull(readFile(reader, "projectDir")));
          externalProject.setBuildDir(assertNotNull(readFile(reader, "buildDir")));
          externalProject.setBuildFile(assertNotNull(readFile(reader, "buildFile")));
          externalProject.setTasks(readTasks(reader));
          externalProject.setSourceSetModel(readSourceSetModel(reader, context));
          externalProject.setChildProjects(readChildProjects(reader, context));
        }
      });
    reader.stepOut();
    return project;
  }

  private static Map<String, DefaultExternalProject> readChildProjects(@NotNull IonReader reader, @NotNull ReadContext context) {
    return readList(reader, "childProjects", () -> readProject(reader, context))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  private static Map<String, DefaultExternalTask> readTasks(IonReader reader) {
    return readList(reader, "tasks", () -> readTask(reader))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  @Nullable
  private static DefaultExternalTask readTask(IonReader reader) {
    if (reader.next() == null) return null;
    reader.stepIn();
    DefaultExternalTask task = new DefaultExternalTask();
    task.setName(assertNotNull(readString(reader, "name")));
    task.setQName(assertNotNull(readString(reader, "qName")));
    task.setDescription(readString(reader, "description"));
    task.setGroup(readString(reader, "group"));
    task.setType(readString(reader, "type"));
    task.setTest(readBoolean(reader, "isTest"));
    task.setJvmTest(readBoolean(reader, "isJvmTest"));
    reader.stepOut();
    return task;
  }

  private static @NotNull DefaultGradleSourceSetModel readSourceSetModel(IonReader reader, ReadContext context) {
    reader.next();
    assertFieldName(reader, "sourceSetModel");
    reader.stepIn();
    DefaultGradleSourceSetModel sourceSetModel = new DefaultGradleSourceSetModel();
    sourceSetModel.setSourceCompatibility(readString(reader, "sourceCompatibility"));
    sourceSetModel.setTargetCompatibility(readString(reader, "targetCompatibility"));
    sourceSetModel.setTaskArtifacts(readFileList(reader, "taskArtifacts"));
    sourceSetModel.setConfigurationArtifacts(readConfigurationArtifacts(reader));
    sourceSetModel.setSourceSets(readSourceSets(reader, context));
    sourceSetModel.setAdditionalArtifacts(readFileList(reader, "additionalArtifacts"));
    reader.stepOut();
    return sourceSetModel;
  }

  private static @NotNull Map<String, Set<File>> readConfigurationArtifacts(IonReader reader) {
    return readMap(reader, "configurationArtifacts", () -> readString(reader, null), () -> readFileSet(reader, null));
  }

  private static @NotNull Map<String, DefaultExternalSourceSet> readSourceSets(IonReader reader, ReadContext context) {
    return readList(reader, "sourceSets", () -> readSourceSet(reader, context))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  @Nullable
  private static DefaultExternalSourceSet readSourceSet(IonReader reader, ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    DefaultExternalSourceSet sourceSet = new DefaultExternalSourceSet();
    sourceSet.setName(readString(reader, "name"));
    sourceSet.setSourceCompatibility(readString(reader, "sourceCompatibility"));
    sourceSet.setTargetCompatibility(readString(reader, "targetCompatibility"));
    sourceSet.setPreview(readBoolean(reader, "isPreview"));
    sourceSet.setArtifacts(readFileList(reader, "artifacts"));
    sourceSet.setDependencies(readDependencies(reader, context));
    sourceSet.setSources(readSourceDirectorySets(reader));
    sourceSet.setJavaToolchainHome(readFile(reader, "javaToolchainHome"));
    reader.stepOut();
    return sourceSet;
  }

  private static Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> readSourceDirectorySets(IonReader reader) {
    return readMap(reader, "sources", () -> readSourceType(reader), () -> readSourceDirectorySet(reader));
  }

  private static ExternalSystemSourceType readSourceType(IonReader reader) {
    return ExternalSystemSourceType.valueOf(assertNotNull(readString(reader, null)));
  }

  private static DefaultExternalSourceDirectorySet readSourceDirectorySet(IonReader reader) {
    reader.next();
    reader.stepIn();
    DefaultExternalSourceDirectorySet directorySet = new DefaultExternalSourceDirectorySet();
    directorySet.setName(assertNotNull(readString(reader, "name")));
    directorySet.setSrcDirs(readFileSet(reader, "srcDirs"));
    directorySet.setGradleOutputDirs(readFileList(reader, "gradleOutputDirs"));
    directorySet.setOutputDir(assertNotNull(readFile(reader, "outputDir")));
    directorySet.setInheritedCompilerOutput(readBoolean(reader, "inheritedCompilerOutput"));
    directorySet.setPatterns(readPatterns(reader));
    directorySet.setFilters(readFilters(reader));
    reader.stepOut();
    return directorySet;
  }

  private static List<DefaultExternalFilter> readFilters(IonReader reader) {
    return readList(reader, "filters", () ->
      readFilter(reader)
    );
  }

  @Nullable
  private static DefaultExternalFilter readFilter(IonReader reader) {
    if (reader.next() == null) return null;
    DefaultExternalFilter filter = new DefaultExternalFilter();
    reader.stepIn();
    filter.setFilterType(assertNotNull(readString(reader, "filterType")));
    filter.setPropertiesAsJsonMap(assertNotNull(readString(reader, "propertiesAsJsonMap")));
    reader.stepOut();
    return filter;
  }

  private static FilePatternSet readPatterns(IonReader reader) {
    reader.next();
    assertFieldName(reader, "patterns");
    reader.stepIn();
    FilePatternSetImpl patternSet = new FilePatternSetImpl();
    patternSet.setIncludes(readStringSet(reader, "includes"));
    patternSet.setExcludes(readStringSet(reader, "excludes"));
    reader.stepOut();
    return patternSet;
  }

  private static Collection<ExternalDependency> readDependencies(IonReader reader, ReadContext context) {
    return readList(reader, "dependencies", () ->
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

