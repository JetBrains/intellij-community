// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.MavenRepositoryModel;
import org.jetbrains.plugins.gradle.model.RepositoriesModel;
import org.jetbrains.plugins.gradle.tooling.internal.MavenRepositoryModelImpl;
import org.jetbrains.plugins.gradle.tooling.internal.RepositoriesModelImpl;
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
public class RepositoriesModelSerializationService implements SerializationService<RepositoriesModel> {
  private final WriteContext myWriteContext = new WriteContext();
  private final ReadContext myReadContext = new ReadContext();

  @Override
  public byte[] write(RepositoriesModel repositoriesModel, Class<? extends RepositoriesModel> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      write(writer, myWriteContext, repositoriesModel);
    }
    return out.toByteArray();
  }

  @Override
  public RepositoriesModel read(byte[] object, Class<? extends RepositoriesModel> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      return read(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends RepositoriesModel> getModelClass() {
    return RepositoriesModel.class;
  }


  private static void write(final IonWriter writer, final WriteContext context, final RepositoriesModel model) throws IOException {
    context.objectCollector.add(model, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeRepositories(writer, context, model.getAll());
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

  @Nullable
  private static RepositoriesModel read(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    RepositoriesModel model =
      context.objectMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<RepositoriesModelImpl>() {

        @Override
        public RepositoriesModelImpl create() {
          RepositoriesModelImpl repositoriesModel = new RepositoriesModelImpl();
          List<MavenRepositoryModel> repositoryModels = readRepositories(reader, context);
          for (MavenRepositoryModel entry : repositoryModels) {
            repositoriesModel.add(entry);
          }
          return repositoriesModel;
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

  @Nullable
  private static MavenRepositoryModel readRepositoryModel(final IonReader reader, ReadContext context) {
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
    private final IntObjectMap<RepositoriesModelImpl> objectMap = new IntObjectMap<>();
    private final IntObjectMap<MavenRepositoryModel> repositoryMap = new IntObjectMap<>();
  }

  private static class WriteContext {
    private final ObjectCollector<RepositoriesModel, IOException> objectCollector = new ObjectCollector<>();
    private final ObjectCollector<MavenRepositoryModel, IOException> repositoryCollector =
      new ObjectCollector<>();
  }
}

