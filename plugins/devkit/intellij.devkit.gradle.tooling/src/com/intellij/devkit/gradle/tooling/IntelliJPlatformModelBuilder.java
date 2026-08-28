// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.gradle.tooling;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reflects over the IntelliJ Platform Gradle Plugin DSL and schedules its release-catalog dump for IDE import.
 */
@ApiStatus.Internal
public final class IntelliJPlatformModelBuilder extends AbstractModelBuilderService {
  private static final String INTELLIJ_PLATFORM_EXTENSION_NAME = "intellijPlatform";
  private static final String INTELLIJ_PLATFORM_DEPENDENCY_CONFIGURATION_CLASS =
    "org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependencyConfiguration";
  private static final String INTELLIJ_PLATFORM_TYPE_CLASS = "org.jetbrains.intellij.platform.gradle.IntelliJPlatformType";
  private static final String DUMP_PRODUCTS_RELEASES_TASK_NAME = "dumpProductsReleases";
  private static final String GRADLE_ACTION_CLASS = "org.gradle.api.Action";
  private static final String PRODUCT_CODE_GETTER = "getCode";

  @Override
  public boolean canBuild(@Nullable String modelName) {
    return IntelliJPlatformGradleModel.class.getName().equals(modelName);
  }

  @Override
  public @Nullable Object buildAll(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context
  ) {
    Object extension = project.getDependencies().getExtensions().findByName(INTELLIJ_PLATFORM_EXTENSION_NAME);
    if (extension == null) return null;

    Task dumpTask = project.getTasks().findByName(DUMP_PRODUCTS_RELEASES_TASK_NAME);
    if (dumpTask != null) {
      Set<String> taskNames = new LinkedHashSet<>(project.getGradle().getStartParameter().getTaskNames());
      taskNames.add(dumpTask.getPath());
      project.getGradle().getStartParameter().setTaskNames(taskNames);
    }

    try {
      return new IntelliJPlatformGradleModelImpl(
        loadDependencyHelperProductCodes(extension.getClass()),
        dumpTask == null ? null : dumpTask.getOutputs().getFiles().getSingleFile().getAbsolutePath()
      );
    }
    catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(this)
      .withKind(Message.Kind.WARNING)
      .withTitle("Gradle import errors")
      .withText("Unable to build IntelliJ Platform project configuration")
      .withException(exception)
      .reportMessage(project);
  }

  private static @NotNull Map<String, String> loadDependencyHelperProductCodes(@NotNull Class<?> extensionClass)
    throws ReflectiveOperationException {
    Class<?> platformTypeClass = extensionClass.getClassLoader().loadClass(INTELLIJ_PLATFORM_TYPE_CLASS);
    Method productCodeGetter = platformTypeClass.getMethod(PRODUCT_CODE_GETTER);
    Map<String, String> productCodeByTypeName = new HashMap<>();
    for (Object platformType : platformTypeClass.getEnumConstants()) {
      String typeName = ((Enum<?>)platformType).name().toLowerCase(Locale.ROOT);
      productCodeByTypeName.put(typeName, (String)productCodeGetter.invoke(platformType));
    }

    Map<String, String> dependencyHelperProductCodes = new TreeMap<>();
    for (Method method : extensionClass.getMethods()) {
      if (!acceptsIntelliJPlatformDependencyConfiguration(method)) continue;

      String productCode = productCodeByTypeName.get(method.getName().toLowerCase(Locale.ROOT));
      if (productCode != null) {
        dependencyHelperProductCodes.put(method.getName(), productCode);
      }
    }
    return dependencyHelperProductCodes;
  }

  private static boolean acceptsIntelliJPlatformDependencyConfiguration(@NotNull Method method) {
    for (Type parameterType : method.getGenericParameterTypes()) {
      if (!(parameterType instanceof ParameterizedType)) continue;

      ParameterizedType parameterizedType = (ParameterizedType)parameterType;
      if (!GRADLE_ACTION_CLASS.equals(parameterizedType.getRawType().getTypeName())) continue;

      for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
        if (INTELLIJ_PLATFORM_DEPENDENCY_CONFIGURATION_CLASS.equals(typeArgument.getTypeName())) {
          return true;
        }
      }
    }
    return false;
  }
}
