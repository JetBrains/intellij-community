// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.projectModel;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.DefaultGradleSourceSetModel;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetSerialisationService;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetSerialisationService.SourceSetModelReadContext;
import com.intellij.gradle.toolingExtension.impl.model.sourceSetModel.GradleSourceSetSerialisationService.SourceSetModelWriteContext;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.DefaultGradleTaskModel;
import com.intellij.gradle.toolingExtension.impl.model.taskModel.GradleTaskSerialisationService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DefaultExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.ObjectFactory;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleExternalProjectSerializationService implements SerializationService<ExternalProject> {

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
  private static final String PROJECT_SOURCE_SET_MODEL_FIELD = "sourceSetModel";
  private static final String PROJECT_TASK_MODEL_FIELD = "taskModel";
  private static final String PROJECT_CHILD_PROJECTS_FIELD = "childProjects";

  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public Class<? extends ExternalProject> getModelClass() {
    return ExternalProject.class;
  }

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
        writeSourceSetModel(writer, context, project);
        writeTaskModel(writer, project);
        writeChildProjects(writer, context, project);
      }
      writer.stepOut();
    });
  }

  private static @Nullable DefaultExternalProject readProject(@NotNull IonReader reader, @NotNull ReadContext context) {
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
          externalProject.setSourceSetModel(readSourceSetModel(reader, context));
          externalProject.setTaskModel(readTaskModel(reader));
          externalProject.setChildProjects(readChildProjects(reader, context));
        }
      });
    reader.stepOut();
    return project;
  }

  private static void writeChildProjects(
    @NotNull IonWriter writer,
    @NotNull WriteContext context,
    @NotNull ExternalProject project
  ) throws IOException {
    writeCollection(writer, PROJECT_CHILD_PROJECTS_FIELD, project.getChildProjects().values(), it ->
      writeProject(writer, context, it)
    );
  }

  private static @NotNull Map<String, DefaultExternalProject> readChildProjects(
    @NotNull IonReader reader,
    @NotNull ReadContext context
  ) {
    return readList(reader, PROJECT_CHILD_PROJECTS_FIELD, () -> readProject(reader, context))
      .stream().collect(Collectors.toMap(it -> it.getName(), it -> it));
  }

  private static void writeSourceSetModel(
    @NotNull IonWriter writer,
    @NotNull WriteContext context,
    @NotNull ExternalProject project
  ) {
    writer.setFieldName(PROJECT_SOURCE_SET_MODEL_FIELD);
    GradleSourceSetSerialisationService.writeSourceSetModel(writer, context.sourceSetModel, project.getSourceSetModel());
  }

  private static @NotNull DefaultGradleSourceSetModel readSourceSetModel(
    @NotNull IonReader reader,
    @NotNull ReadContext context
  ) {
    assertNotNull(reader.next());
    assertFieldName(reader, PROJECT_SOURCE_SET_MODEL_FIELD);
    return GradleSourceSetSerialisationService.readSourceSetModel(reader, context.sourceSetModel);
  }

  private static void writeTaskModel(
    @NotNull IonWriter writer,
    @NotNull ExternalProject project
  ) {
    writer.setFieldName(PROJECT_TASK_MODEL_FIELD);
    GradleTaskSerialisationService.writeTaskModel(writer, project.getTaskModel());
  }

  private static @NotNull DefaultGradleTaskModel readTaskModel(
    @NotNull IonReader reader
  ) {
    assertNotNull(reader.next());
    assertFieldName(reader, PROJECT_TASK_MODEL_FIELD);
    return GradleTaskSerialisationService.readTaskModel(reader);
  }

  private static class ReadContext {

    private final SourceSetModelReadContext sourceSetModel = new SourceSetModelReadContext();

    private final IntObjectMap<DefaultExternalProject> myProjectsMap = new IntObjectMap<>();

    public IntObjectMap<DefaultExternalProject> getProjectsMap() {
      return myProjectsMap;
    }
  }

  private static class WriteContext {

    private final SourceSetModelWriteContext sourceSetModel = new SourceSetModelWriteContext();

    private final ObjectCollector<ExternalProject, IOException> myProjectsCollector =
      new ObjectCollector<>();

    public ObjectCollector<ExternalProject, IOException> getProjectsCollector() {
      return myProjectsCollector;
    }
  }
}

