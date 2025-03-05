// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.buildScriptClasspathModel;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ClasspathEntryModel;
import org.jetbrains.plugins.gradle.model.GradleBuildScriptClasspathModel;
import org.jetbrains.plugins.gradle.tooling.internal.ClasspathEntryModelImpl;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.SimpleObjectFactory;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public final class GradleBuildScriptClasspathSerializationService implements SerializationService<GradleBuildScriptClasspathModel> {
  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(GradleBuildScriptClasspathModel classpathModel, Class<? extends GradleBuildScriptClasspathModel> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      write(writer, myWriteContext, classpathModel);
    }
    return out.toByteArray();
  }

  @Override
  public GradleBuildScriptClasspathModel read(byte[] object, Class<? extends GradleBuildScriptClasspathModel> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return read(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends GradleBuildScriptClasspathModel> getModelClass() {
    return GradleBuildScriptClasspathModel.class;
  }


  private static void write(final IonWriter writer, final WriteContext context, final GradleBuildScriptClasspathModel model) throws IOException {
    context.objectCollector.add(model, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          if (!context.isFirstModelWritten) {
            context.isFirstModelWritten = true;
            writeString(writer, "gradleVersion", model.getGradleVersion());
            writeFile(writer, "gradleHomeDir", model.getGradleHomeDir());
          }
          writeClasspath(writer, model.getClasspath());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeClasspath(IonWriter writer, Set<? extends ClasspathEntryModel> classpath) throws IOException {
    writer.setFieldName("classpath");
    writer.stepIn(IonType.LIST);
    for (ClasspathEntryModel entry : classpath) {
      writeClasspathEntry(writer, entry);
    }
    writer.stepOut();
  }

  private static void writeClasspathEntry(IonWriter writer, ClasspathEntryModel entry) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeStrings(writer, "classes", entry.getClasses());
    writeStrings(writer, "sources", entry.getSources());
    writeStrings(writer, "javadoc", entry.getJavadoc());
    writer.stepOut();
  }

  private static @Nullable GradleBuildScriptClasspathModel read(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    DefaultGradleBuildScriptClasspathModel model =
      context.objectMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<DefaultGradleBuildScriptClasspathModel>() {
        @Override
        public DefaultGradleBuildScriptClasspathModel create() {
          DefaultGradleBuildScriptClasspathModel classpathModel = new DefaultGradleBuildScriptClasspathModel();
          if (!context.isFirstModelRead) {
            context.isFirstModelRead = true;
            context.gradleVersion = assertNotNull(readString(reader, "gradleVersion"));
            context.gradleHomeDir = readFile(reader, "gradleHomeDir");
          }
          classpathModel.setGradleVersion(context.gradleVersion);
          classpathModel.setGradleHomeDir(context.gradleHomeDir);
          List<ClasspathEntryModel> classpathEntries = readClasspath(reader);
          for (ClasspathEntryModel entry : classpathEntries) {
            classpathModel.add(entry);
          }
          return classpathModel;
        }
      });
    reader.stepOut();
    return model;
  }

  private static List<ClasspathEntryModel> readClasspath(IonReader reader) {
    List<ClasspathEntryModel> list = new ArrayList<>();
    reader.next();
    reader.stepIn();
    ClasspathEntryModel entry;
    while ((entry = readClasspathEntry(reader)) != null) {
      list.add(entry);
    }
    reader.stepOut();
    return list;
  }

  private static ClasspathEntryModel readClasspathEntry(IonReader reader) {
    if (reader.next() == null) return null;
    reader.stepIn();
    ClasspathEntryModel entryModel = new ClasspathEntryModelImpl(
      readFileSet(reader, null),
      readFileSet(reader, null),
      readFileSet(reader, null)
    );
    reader.stepOut();
    return entryModel;
  }

  private static class ReadContext {
    private boolean isFirstModelRead;
    private File gradleHomeDir;
    private String gradleVersion;
    private final IntObjectMap<DefaultGradleBuildScriptClasspathModel> objectMap = new IntObjectMap<>();
  }

  private static class WriteContext {
    private boolean isFirstModelWritten;
    private final ObjectCollector<GradleBuildScriptClasspathModel, IOException> objectCollector =
      new ObjectCollector<>();
  }
}

