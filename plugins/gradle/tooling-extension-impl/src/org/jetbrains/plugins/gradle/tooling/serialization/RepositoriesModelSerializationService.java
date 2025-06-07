// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.MavenRepositoryModel;
import org.jetbrains.plugins.gradle.model.RepositoryModels;
import org.jetbrains.plugins.gradle.tooling.internal.DefaultRepositoryModels;
import org.jetbrains.plugins.gradle.tooling.internal.MavenRepositoryModelImpl;
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
public class RepositoriesModelSerializationService implements SerializationService<RepositoryModels> {
  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(RepositoryModels repositoryModels, Class<? extends RepositoryModels> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      write(writer, myWriteContext, repositoryModels);
    }
    return out.toByteArray();
  }

  @Override
  public RepositoryModels read(byte[] object, Class<? extends RepositoryModels> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return read(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends RepositoryModels> getModelClass() {
    return RepositoryModels.class;
  }


  private static void write(final IonWriter writer, final WriteContext context, final RepositoryModels model) throws IOException {
    context.objectCollector.add(model, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeRepositories(writer, context, model.getRepositories());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeRepositories(IonWriter writer,
                                        WriteContext context,
                                        Collection<MavenRepositoryModel> repositoryModels) throws IOException {
    writer.setFieldName("repositories");
    writer.stepIn(IonType.LIST);
    for (MavenRepositoryModel repositoryModel : repositoryModels) {
      writeRepositoryModel(writer, context, repositoryModel);
    }
    writer.stepOut();
  }

  private static void writeRepositoryModel(final IonWriter writer,
                                           final WriteContext context,
                                           final MavenRepositoryModel repositoryModel) throws IOException {
    context.repositoryCollector.add(repositoryModel, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "name", repositoryModel.getName());
          writeString(writer, "url", repositoryModel.getUrl());
        }
        writer.stepOut();
      }
    });
  }

  private static @Nullable RepositoryModels read(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    RepositoryModels model =
      context.objectMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<DefaultRepositoryModels>() {

        @Override
        public DefaultRepositoryModels create() {
          List<MavenRepositoryModel> repositories = readRepositories(reader, context);
          return new DefaultRepositoryModels(repositories);
        }
      });
    reader.stepOut();
    return model;
  }

  private static List<MavenRepositoryModel> readRepositories(IonReader reader, ReadContext context) {
    List<MavenRepositoryModel> list = new ArrayList<>();
    reader.next();
    reader.stepIn();
    MavenRepositoryModel entry;
    while ((entry = readRepositoryModel(reader, context)) != null) {
      list.add(entry);
    }
    reader.stepOut();
    return list;
  }

  private static @Nullable MavenRepositoryModel readRepositoryModel(final IonReader reader, ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    MavenRepositoryModel dependency =
      context.repositoryMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<MavenRepositoryModel>() {
        @Override
        public MavenRepositoryModel create() {
          return new MavenRepositoryModelImpl(readString(reader, "name"), readString(reader, "url"));
        }
      });
    reader.stepOut();
    return dependency;
  }

  private static class ReadContext {
    private final IntObjectMap<DefaultRepositoryModels> objectMap = new IntObjectMap<>();
    private final IntObjectMap<MavenRepositoryModel> repositoryMap = new IntObjectMap<>();
  }

  private static class WriteContext {
    private final ObjectCollector<RepositoryModels, IOException> objectCollector = new ObjectCollector<>();
    private final ObjectCollector<MavenRepositoryModel, IOException> repositoryCollector =
      new ObjectCollector<>();
  }
}

