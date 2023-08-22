// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingConfig;
import org.jetbrains.plugins.gradle.model.AnnotationProcessingModel;
import org.jetbrains.plugins.gradle.tooling.internal.AnnotationProcessingConfigImpl;
import org.jetbrains.plugins.gradle.tooling.internal.AnnotationProcessingModelImpl;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.Supplier;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

public final class AnnotationProcessingModelSerializationService implements SerializationService<AnnotationProcessingModel> {
  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(AnnotationProcessingModel annotationProcessingModel, Class<? extends AnnotationProcessingModel> modelClazz)
    throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      write(writer, myWriteContext, annotationProcessingModel);
    }
    return out.toByteArray();
  }

  @Override
  public AnnotationProcessingModel read(byte[] object, Class<? extends AnnotationProcessingModel> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return read(reader, myReadContext);
    }
  }

  private static void write(final IonWriter writer, final WriteContext context, final AnnotationProcessingModel model) throws IOException {
    context.objectCollector.add(model, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeConfigs(writer, context, model.allConfigs());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeConfigs(final IonWriter writer,
                                   final WriteContext context,
                                   Map<String, AnnotationProcessingConfig> configs) throws IOException {
    writeMap(writer, "configs", configs, s -> writer.writeString(s), config -> writeConfig(writer, context, config));
  }

  private static void writeConfig(final IonWriter writer,
                                  final WriteContext context,
                                  final AnnotationProcessingConfig config) throws IOException {
    context.configCollector.add(config, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeStrings(writer, "args", config.getAnnotationProcessorArguments());
          writeStrings(writer, "paths", config.getAnnotationProcessorPath());
          writeString(writer, "output", config.getProcessorOutput());
          writeBoolean(writer, "isTestSources", config.isTestSources());
        }
        writer.stepOut();
      }
    });
  }

  private static AnnotationProcessingModel read(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    AnnotationProcessingModelImpl model =
      context.objectMap
        .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.SimpleObjectFactory<AnnotationProcessingModelImpl>() {
          @Override
          public AnnotationProcessingModelImpl create() {
            Map<String, AnnotationProcessingConfig> configs = readConfigs(reader, context);
            return new AnnotationProcessingModelImpl(configs);
          }
        });
    reader.stepOut();
    return model;
  }

  private static Map<String, AnnotationProcessingConfig> readConfigs(final IonReader reader, final ReadContext context) {
    return readMap(reader, new Supplier<String>() {
      @Override
      public String get() {
        return readString(reader, null);
      }
    }, new Supplier<AnnotationProcessingConfig>() {
      @Override
      public AnnotationProcessingConfig get() {
        return readConfig(reader, context);
      }
    });
  }

  private static AnnotationProcessingConfig readConfig(final IonReader reader, final ReadContext context) {
    reader.next();
    reader.stepIn();
    AnnotationProcessingConfigImpl config =
      context.configMap
        .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.SimpleObjectFactory<AnnotationProcessingConfigImpl>() {
          @Override
          public AnnotationProcessingConfigImpl create() {
            List<String> args = readStringList(reader);
            List<File> files = readFiles(reader);
            String output = readString(reader, "output");
            boolean isTest = readBoolean(reader,"isTestSources");
            return new AnnotationProcessingConfigImpl(files, args, output, isTest);
          }
        });
    reader.stepOut();
    return config;
  }

  @Override
  public Class<? extends AnnotationProcessingModel> getModelClass() {
    return AnnotationProcessingModel.class;
  }

  private static class WriteContext {
    private final ObjectCollector<AnnotationProcessingModel, IOException> objectCollector =
      new ObjectCollector<>();

    private final ObjectCollector<AnnotationProcessingConfig, IOException> configCollector =
      new ObjectCollector<>();
  }

  private static class ReadContext {
    private final IntObjectMap<AnnotationProcessingModelImpl> objectMap = new IntObjectMap<>();
    private final IntObjectMap<AnnotationProcessingConfigImpl> configMap = new IntObjectMap<>();
  }
}
