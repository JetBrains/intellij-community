// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import com.amazon.ion.util.IonStreamUtils;
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

import static com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencySerialisationUtil.readDependency;
import static com.intellij.gradle.toolingExtension.impl.model.dependencyModel.GradleDependencySerialisationUtil.writeDependency;
import static com.intellij.util.ArrayUtilRt.EMPTY_STRING_ARRAY;
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

  private static void writeProject(final IonWriter writer,
                                   final WriteContext context,
                                   final ExternalProject project) throws IOException {
    context.getProjectsCollector().add(project, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
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
          writeTasks(writer, project.getTasks());
          writeSourceSetModel(writer, context, project.getSourceSetModel());
          writeChildProjects(writer, context, project.getChildProjects());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeChildProjects(IonWriter writer,
                                         WriteContext context,
                                         Map<String, ? extends ExternalProject> projects) throws IOException {
    writer.setFieldName("childProjects");
    writer.stepIn(IonType.LIST);
    for (ExternalProject project : projects.values()) {
      writeProject(writer, context, project);
    }
    writer.stepOut();
  }

  private static void writeSourceSetModel(IonWriter writer,
                                          WriteContext context,
                                          GradleSourceSetModel sourceSetModel) throws IOException {
    writer.setFieldName("sourceSetModel");
    writer.stepIn(IonType.STRUCT);

    writeString(writer, "sourceCompatibility", sourceSetModel.getSourceCompatibility());
    writeString(writer, "targetCompatibility", sourceSetModel.getTargetCompatibility());
    writeFiles(writer, "taskArtifacts", sourceSetModel.getTaskArtifacts());
    writeConfigurationArtifacts(writer, sourceSetModel.getConfigurationArtifacts());
    writeSourceSets(writer, context, sourceSetModel.getSourceSets());
    writeFiles(writer, "additionalArtifacts", sourceSetModel.getAdditionalArtifacts());

    writer.stepOut();
  }

  private static void writeSourceSets(IonWriter writer,
                                      WriteContext context,
                                      Map<String, ? extends ExternalSourceSet> sets) throws IOException {
    writer.setFieldName("sourceSets");
    writer.stepIn(IonType.LIST);
    for (ExternalSourceSet sourceSet : sets.values()) {
      writeSourceSet(writer, context, sourceSet);
    }
    writer.stepOut();
  }

  private static void writeSourceSet(IonWriter writer,
                                     WriteContext context,
                                     ExternalSourceSet sourceSet) throws IOException {
    writer.stepIn(IonType.STRUCT);

    writeString(writer, "name", sourceSet.getName());
    writeString(writer, "sourceCompatibility", sourceSet.getSourceCompatibility());
    writeString(writer, "targetCompatibility", sourceSet.getTargetCompatibility());
    writeBoolean(writer, "isPreview", sourceSet.isPreview());
    writeFiles(writer, "artifacts", sourceSet.getArtifacts());
    writeDependencies(writer, context, sourceSet.getDependencies());
    writeSourceDirectorySets(writer, sourceSet.getSources());
    writeFile(writer, "javaToolchainHome", sourceSet.getJavaToolchainHome());

    writer.stepOut();
  }

  private static void writeSourceDirectorySets(IonWriter writer,
                                               Map<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sources)
    throws IOException {
    writer.setFieldName("sources");
    writer.stepIn(IonType.LIST);
    for (Map.Entry<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> entry : sources.entrySet()) {
      writeSourceDirectorySet(writer, entry.getKey(), entry.getValue());
    }
    writer.stepOut();
  }

  private static void writeSourceDirectorySet(IonWriter writer,
                                              IExternalSystemSourceType sourceType,
                                              ExternalSourceDirectorySet directorySet)
    throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "sourceType", ExternalSystemSourceType.from(sourceType).name());
    writeString(writer, "name", directorySet.getName());
    writeFiles(writer, "srcDirs", directorySet.getSrcDirs());
    writeFiles(writer, "gradleOutputDirs", directorySet.getGradleOutputDirs());
    writeFile(writer, "outputDir", directorySet.getOutputDir());
    writer.setFieldName("inheritedCompilerOutput");
    writer.writeBool(directorySet.isCompilerOutputPathInherited());
    writePatterns(writer, directorySet.getPatterns());
    writeFilters(writer, directorySet.getFilters());
    writer.stepOut();
  }

  private static void writeFilters(IonWriter writer, List<? extends ExternalFilter> filters) throws IOException {
    writer.setFieldName("filters");
    writer.stepIn(IonType.LIST);
    for (ExternalFilter filter : filters) {
      writeFilter(writer, filter);
    }
    writer.stepOut();
  }

  private static void writeFilter(IonWriter writer, ExternalFilter filter) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "filterType", filter.getFilterType());
    writeString(writer, "propertiesAsJsonMap", filter.getPropertiesAsJsonMap());
    writer.stepOut();
  }

  private static void writePatterns(IonWriter writer, FilePatternSet patterns) throws IOException {
    writer.setFieldName("patterns");
    writer.stepIn(IonType.STRUCT);
    writer.setFieldName("includes");
    IonStreamUtils.writeStringList(writer, patterns.getIncludes().toArray(EMPTY_STRING_ARRAY));
    writer.setFieldName("excludes");
    IonStreamUtils.writeStringList(writer, patterns.getExcludes().toArray(EMPTY_STRING_ARRAY));
    writer.stepOut();
  }

  private static void writeDependencies(
    IonWriter writer,
    WriteContext context,
    Collection<? extends ExternalDependency> dependencies
  ) throws IOException {
    writeCollection(writer, "dependencies", dependencies, it ->
      writeDependency(writer, context.myDependencyContext, it)
    );
  }

  private static void writeTasks(IonWriter writer, Map<String, ? extends ExternalTask> tasks) throws IOException {
    writer.setFieldName("tasks");
    writer.stepIn(IonType.LIST);
    for (ExternalTask task : tasks.values()) {
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
    writer.stepOut();
  }

  private static void writeConfigurationArtifacts(final IonWriter writer, Map<String, Set<File>> configuration) throws IOException {
    writeMap(writer, "configurationArtifacts", configuration, s -> writer.writeString(s), files -> writeFiles(writer, "value", files));
  }

  @Nullable
  private static DefaultExternalProject readProject(@NotNull final IonReader reader,
                                                    @NotNull final ReadContext context) {
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
          File projectDir = readFile(reader, "projectDir");
          if (projectDir != null) {
            externalProject.setProjectDir(projectDir);
          }
          File buildDir = readFile(reader, "buildDir");
          if (buildDir != null) {
            externalProject.setBuildDir(buildDir);
          }
          File buildFile = readFile(reader, "buildFile");
          if (buildFile != null) {
            externalProject.setBuildFile(buildFile);
          }
          readTasks(reader, externalProject);
          externalProject.setSourceSetModel(readSourceSetModel(reader, context));
          externalProject.setChildProjects(readProjects(reader, context));
        }
      });
    reader.stepOut();
    return project;
  }

  private static Map<String, DefaultExternalProject> readProjects(@NotNull IonReader reader,
                                                                  @NotNull final ReadContext context) {
    Map<String, DefaultExternalProject> map = new TreeMap<>();
    reader.next();
    reader.stepIn();
    DefaultExternalProject project;
    while ((project = readProject(reader, context)) != null) {
      map.put(project.getName(), project);
    }
    reader.stepOut();
    return map;
  }

  private static void readTasks(IonReader reader, DefaultExternalProject project) {
    reader.next();
    reader.stepIn();
    Map<String, DefaultExternalTask> tasks = new HashMap<>();
    DefaultExternalTask task;
    while ((task = readTask(reader)) != null) {
      tasks.put(task.getName(), task);
    }
    project.setTasks(tasks);
    reader.stepOut();
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
    sourceSetModel.setAdditionalArtifacts(readFileList(reader, null));
    reader.stepOut();
    return sourceSetModel;
  }

  private static @NotNull Map<String, Set<File>> readConfigurationArtifacts(IonReader reader) {
    return readMap(reader, "configurationArtifacts", () -> readString(reader, null), () -> readFileSet(reader, null));
  }

  private static @NotNull Map<String, DefaultExternalSourceSet> readSourceSets(IonReader reader, ReadContext context) {
    reader.next();
    assertFieldName(reader, "sourceSets");
    reader.stepIn();
    Map<String, DefaultExternalSourceSet> sourceSets = new LinkedHashMap<>();
    DefaultExternalSourceSet sourceSet;
    while ((sourceSet = readSourceSet(reader, context)) != null) {
      sourceSets.put(sourceSet.getName(), sourceSet);
    }
    reader.stepOut();
    return sourceSets;
  }

  @Nullable
  private static DefaultExternalSourceSet readSourceSet(IonReader reader,
                                                        ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    DefaultExternalSourceSet sourceSet = new DefaultExternalSourceSet();
    sourceSet.setName(readString(reader, "name"));
    sourceSet.setSourceCompatibility(readString(reader, "sourceCompatibility"));
    sourceSet.setTargetCompatibility(readString(reader, "targetCompatibility"));
    sourceSet.setPreview(readBoolean(reader, "isPreview"));
    sourceSet.setArtifacts(readFileList(reader, null));
    sourceSet.setDependencies(readDependencies(reader, context));
    sourceSet.setSources(readSourceDirectorySets(reader));
    sourceSet.setJavaToolchainHome(readFile(reader, "javaToolchainHome"));
    reader.stepOut();
    return sourceSet;
  }

  private static Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> readSourceDirectorySets(IonReader reader) {
    reader.next();
    reader.stepIn();
    Map<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> map =
      new HashMap<>();
    Map.Entry<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> entry;
    while ((entry = readSourceDirectorySet(reader)) != null) {
      map.put(entry.getKey(), entry.getValue());
    }
    reader.stepOut();
    return map;
  }

  @Nullable
  private static Map.Entry<ExternalSystemSourceType, DefaultExternalSourceDirectorySet> readSourceDirectorySet(IonReader reader) {
    if (reader.next() == null) return null;
    reader.stepIn();
    ExternalSystemSourceType sourceType = ExternalSystemSourceType.valueOf(assertNotNull(readString(reader, "sourceType")));
    DefaultExternalSourceDirectorySet directorySet = new DefaultExternalSourceDirectorySet();
    directorySet.setName(assertNotNull(readString(reader, "name")));
    directorySet.setSrcDirs(readFileSet(reader, null));
    directorySet.setGradleOutputDirs(readFileList(reader, null));
    File outputDir = readFile(reader, "outputDir");
    if (outputDir != null) {
      directorySet.setOutputDir(outputDir);
    }
    directorySet.setInheritedCompilerOutput(readBoolean(reader, "inheritedCompilerOutput"));
    FilePatternSet patternSet = readFilePattern(reader);
    directorySet.setExcludes(patternSet.getExcludes());
    directorySet.setIncludes(patternSet.getIncludes());
    directorySet.setFilters(readFilters(reader));
    reader.stepOut();
    return new AbstractMap.SimpleEntry<>(sourceType, directorySet);
  }

  private static List<DefaultExternalFilter> readFilters(IonReader reader) {
    reader.next();
    reader.stepIn();
    List<DefaultExternalFilter> list = new ArrayList<>();
    DefaultExternalFilter filter;
    while ((filter = readFilter(reader)) != null) {
      list.add(filter);
    }
    reader.stepOut();
    return list;
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

  private static FilePatternSet readFilePattern(IonReader reader) {
    reader.next();
    reader.stepIn();
    FilePatternSetImpl patternSet = new FilePatternSetImpl();
    patternSet.setIncludes(readStringSet(reader, null));
    patternSet.setExcludes(readStringSet(reader, null));
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

