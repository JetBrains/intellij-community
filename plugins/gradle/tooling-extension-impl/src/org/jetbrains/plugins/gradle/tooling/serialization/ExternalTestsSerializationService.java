// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestSourceMapping;
import org.jetbrains.plugins.gradle.model.tests.DefaultExternalTestsModel;
import org.jetbrains.plugins.gradle.model.tests.ExternalTestSourceMapping;
import org.jetbrains.plugins.gradle.model.tests.ExternalTestsModel;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.SimpleObjectFactory;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

/**
 * @author Vladislav.Soroka
 */
public final class ExternalTestsSerializationService implements SerializationService<ExternalTestsModel> {
  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(ExternalTestsModel testsModel, Class<? extends ExternalTestsModel> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      write(writer, myWriteContext, testsModel);
    }
    return out.toByteArray();
  }

  @Override
  public ExternalTestsModel read(byte[] object, Class<? extends ExternalTestsModel> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return read(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends ExternalTestsModel> getModelClass() {
    return ExternalTestsModel.class;
  }


  private static void write(final IonWriter writer, final WriteContext context, final ExternalTestsModel model) throws IOException {
    context.objectCollector.add(model, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeTestSourceMappings(writer, context, model.getTestSourceMappings());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeTestSourceMappings(IonWriter writer,
                                              WriteContext context,
                                              Collection<ExternalTestSourceMapping> sourceMappings) throws IOException {
    writer.setFieldName("sourceTestMappings");
    writer.stepIn(IonType.LIST);
    for (ExternalTestSourceMapping mapping : sourceMappings) {
      writeTestSourceMapping(writer, context, mapping);
    }
    writer.stepOut();
  }

  private static void writeTestSourceMapping(final IonWriter writer,
                                             final WriteContext context,
                                             final ExternalTestSourceMapping testSourceMapping) throws IOException {
    context.mappingCollector.add(testSourceMapping, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "testName", testSourceMapping.getTestName());
          writeString(writer, "testTaskPath", testSourceMapping.getTestTaskPath());
          writeStrings(writer, "sourceFolders", testSourceMapping.getSourceFolders());
        }
        writer.stepOut();
      }
    });
  }

  private static @Nullable ExternalTestsModel read(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    ExternalTestsModel model =
      context.objectMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<ExternalTestsModel>() {
      @Override
      public ExternalTestsModel create() {
        DefaultExternalTestsModel testsModel = new DefaultExternalTestsModel();
        testsModel.setSourceTestMappings(readTestSourceMappings(reader, context));
        return testsModel;
      }
    });
    reader.stepOut();
    return model;
  }

  private static List<ExternalTestSourceMapping> readTestSourceMappings(IonReader reader, ReadContext context) {
    List<ExternalTestSourceMapping> list = new ArrayList<>();
    reader.next();
    reader.stepIn();
    ExternalTestSourceMapping testSourceMapping;
    while ((testSourceMapping = readTestSourceMapping(reader, context)) != null) {
      list.add(testSourceMapping);
    }
    reader.stepOut();
    return list;
  }

  private static @Nullable ExternalTestSourceMapping readTestSourceMapping(final IonReader reader, ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    ExternalTestSourceMapping dependency =
      context.testSourceMapping.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<ExternalTestSourceMapping>() {
        @Override
        public ExternalTestSourceMapping create() {
          DefaultExternalTestSourceMapping mapping = new DefaultExternalTestSourceMapping();
          mapping.setTestName(readString(reader, "testName"));
          mapping.setTestTaskPath(assertNotNull(readString(reader, "testTaskPath")));
          mapping.setSourceFolders(readStringSet(reader, null));
          return mapping;
        }
      });
    reader.stepOut();
    return dependency;
  }

  private static class ReadContext {
    private final IntObjectMap<ExternalTestsModel> objectMap = new IntObjectMap<>();
    private final IntObjectMap<ExternalTestSourceMapping> testSourceMapping = new IntObjectMap<>();
  }

  private static class WriteContext {
    private final ObjectCollector<ExternalTestsModel, IOException> objectCollector = new ObjectCollector<>();
    private final ObjectCollector<ExternalTestSourceMapping, IOException> mappingCollector =
      new ObjectCollector<>();
  }
}

