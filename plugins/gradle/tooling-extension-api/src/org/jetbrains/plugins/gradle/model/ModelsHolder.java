/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.util.io.FileUtilRt;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.model.ProjectModel;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.ToolingSerializer;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 */
public abstract class ModelsHolder<B extends BuildModel, P extends ProjectModel> implements Serializable {

  @NotNull private final B myRootModel;
  @NotNull private final Map<String, Object> myModelsById = new LinkedHashMap<>();
  @Nullable private ToolingSerializer mySerializer;
  @Nullable private Consumer<Object> myPathsConverter;

  public ModelsHolder(@NotNull B rootModel) {
    myRootModel = rootModel;
  }

  @ApiStatus.Internal
  public void initToolingSerializer() {
    mySerializer = new ToolingSerializer();
  }

  @ApiStatus.Internal
  public void applyPathsConverter(@NotNull Consumer<Object> pathsConverter) {
    myPathsConverter = pathsConverter;
  }

  @NotNull
  protected B getRootModel() {
    return myRootModel;
  }

  /**
   * Get model for the root build
   */
  @Nullable
  public <T> T getModel(@NotNull Class<T> modelClazz) {
    return getModel(getRootModel(), modelClazz);
  }

  /**
   * Get model for the specified build
   */
  @Nullable
  public <T> T getModel(@NotNull B build, @NotNull Class<T> modelClazz) {
    String key = extractMapKey(modelClazz, build.getBuildIdentifier());
    return getModel(modelClazz, key);
  }

  /**
   * Get model for the project
   */
  @Nullable
  public <T> T getModel(@NotNull P project, @NotNull Class<T> modelClazz) {
    ProjectIdentifier projectIdentifier = getProjectIdentifier(project);
    String key = extractMapKey(modelClazz, projectIdentifier);
    return getModel(modelClazz, key);
  }

  @Nullable
  private <T> T getModel(@NotNull Class<T> modelClazz, @NotNull String key) {
    Object model = myModelsById.get(key);
    if (model == null) return null;
    if (modelClazz.isInstance(model)) {
      //noinspection unchecked
      return (T)model;
    }
    else {
      deserializeAllDataOfTheType(modelClazz);
      //noinspection unchecked
      return (T)myModelsById.get(key);
    }
  }

  /**
   * Deserialize all data of the model type to ensure the same order which has been used when adding the data.
   */
  private <T> void deserializeAllDataOfTheType(@NotNull Class<T> modelClazz) {
    String keyPrefix = getModelKeyPrefix(modelClazz);
    for (Iterator<Map.Entry<String, Object>> iterator = myModelsById.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Object> entry = iterator.next();
      String key = entry.getKey();
      if (key.startsWith(keyPrefix)) {
        if (mySerializer == null || !(entry.getValue() instanceof byte[])) {
          iterator.remove();
          continue;
        }
        try {
          T deserializedData = mySerializer.read((byte[])entry.getValue(), modelClazz);
          if (modelClazz.isInstance(deserializedData)) {
            if (myPathsConverter != null) {
              myPathsConverter.accept(deserializedData);
            }
            myModelsById.put(key, deserializedData);
          }
          else {
            iterator.remove();
          }
        }
        catch (Exception e) {
          reportError(entry.getKey(), e);
          iterator.remove();
        }
      }
    }
  }

  private void reportError(String key, Exception e) {
    //noinspection UseOfSystemOutOrSystemErr
    System.err.println(e.getMessage());
    tryReportingViaIdeaLogger(e, key);
  }

  /**
   * Try to report error to IDEA logging subsystem.
   * <p>
   * The ModelsHolder can be used both, in Gradle Daemon process and in IntelliJ IDEA process.
   * In latter case, it would be good to report errors to IDEA log, as failure to deserialize an entity
   * can lead to unexpected behavior (e.g., broken project model after import
   * @param e the exception
   * @param key key (name of the model) that was not possible to deserialize.
   */
  private void tryReportingViaIdeaLogger(Exception e, String key) {
    try {
      Class<? extends ModelsHolder> aClass = getClass();
      Class<?> loggerClazz = aClass.getClassLoader().loadClass("com.intellij.openapi.diagnostic.Logger");
      Method getInstanceMethod = loggerClazz.getMethod("getInstance", Class.class);
      Object logger = getInstanceMethod.invoke(null, aClass);
      Method errorMethod = loggerClazz.getMethod("error", String.class, Throwable.class);
      errorMethod.invoke(logger, "Failed to parse model with key [" + key + "]", e);
    } catch (Exception ignore) {
    }
  }

  public boolean hasModulesWithModel(@NotNull Class modelClazz) {
    String keyPrefix = getModelKeyPrefix(modelClazz);
    for (String key : myModelsById.keySet()) {
      if (key.startsWith(keyPrefix)) return true;
    }
    return false;
  }

  public void addModel(@NotNull Object model, @NotNull Class modelClazz) {
    addModel(model, modelClazz, getRootModel());
  }

  public void addModel(@NotNull Object model, @NotNull Class modelClazz, @NotNull P project) {
    ProjectIdentifier projectIdentifier = getProjectIdentifier(project);
    myModelsById.put(extractMapKey(modelClazz, projectIdentifier), model);
  }

  public void addModel(@NotNull Object model, @NotNull Class modelClazz, @NotNull B build) {
    myModelsById.put(extractMapKey(modelClazz, build.getBuildIdentifier()), model);
  }

  @NotNull
  private static String getModelKeyPrefix(@NotNull Class modelClazz) {
    return modelClazz.getSimpleName() + modelClazz.getName().hashCode();
  }

  @NotNull
  private String extractMapKey(@NotNull Class modelClazz, @NotNull ProjectIdentifier projectIdentifier) {
    String modelKeyPrefix = getModelKeyPrefix(modelClazz);
    String buildKeyPrefix = getBuildKeyPrefix(projectIdentifier.getBuildIdentifier());
    return modelKeyPrefix + '/' + (buildKeyPrefix + projectIdentifier.getProjectPath());
  }

  @NotNull
  private String extractMapKey(@NotNull Class modelClazz, @NotNull BuildIdentifier buildIdentifier) {
    String modelKeyPrefix = getModelKeyPrefix(modelClazz);
    String buildKeyPrefix = getBuildKeyPrefix(buildIdentifier);
    return modelKeyPrefix + '/' + buildKeyPrefix + ":";
  }

  @NotNull
  protected String getBuildKeyPrefix(@NotNull BuildIdentifier buildIdentifier) {
    String path = buildIdentifier.getRootDir().getPath();
    String systemIndependentName = FileUtilRt.toSystemIndependentName(path);
    return String.valueOf(systemIndependentName.hashCode());
  }

  private ProjectIdentifier getProjectIdentifier(@NotNull P project) {
    ProjectIdentifier projectIdentifier;
    if (project instanceof IdeaModule) {
      // do not use IdeaModule#getProjectIdentifier, it returns project identifier of the root project
      projectIdentifier = ((IdeaModule)project).getGradleProject().getProjectIdentifier();
    }
    else {
      projectIdentifier = project.getProjectIdentifier();
    }
    return projectIdentifier;
  }

  @Override
  public String toString() {
    return "Models{" +
           "rootModel=" + myRootModel +
           ", myModelsById=" + myModelsById +
           '}';
  }

  public boolean hasModels() {
    return !myModelsById.isEmpty();
  }
}
