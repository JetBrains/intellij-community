// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.UnknownConfigurationException;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.initialization.dsl.VersionCatalogBuilder;
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.catalog.DefaultVersionCatalogBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.VersionCatalogsModel;
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.internal.VersionCatalogsModelImpl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

public class VersionCatalogsModelBuilder extends AbstractModelBuilderService {
  @Override
  public Object buildAll(@NotNull String modelName, @NotNull Project project, @NotNull ModelBuilderContext context) {
    Map<String, String> result = new HashMap<>();
    SettingsInternal settings = ((GradleInternal)project.getGradle()).getSettings();
    MutableVersionCatalogContainer catalogs = settings.getDependencyResolutionManagement().getVersionCatalogs();
    for (VersionCatalogBuilder builder : catalogs) {
      if (!(builder instanceof DefaultVersionCatalogBuilder)) {
        continue;
      }
      DefaultVersionCatalogBuilder catalogBuilder = (DefaultVersionCatalogBuilder)builder;
      catalogBuilder.build(); // trigger resolution, if not resolved yet.
      String name = catalogBuilder.getName();
      DependencyResolutionServices service = extractDependencyResolutionService(catalogBuilder);
      if (service == null) {
        continue;
      }

      try {
        Configuration catalogImportConf = service.getConfigurationContainer()
          .getByName("incomingCatalogFor" + capitalize(name) + "0");
        for (ResolvedArtifactResult artifact : catalogImportConf.getIncoming().getArtifacts().getArtifacts()) {
          result.put(name, artifact.getFile().getAbsolutePath().replace('\\', '/'));
        }
      } catch (UnknownConfigurationException ignore) {
      }

    }
    return new VersionCatalogsModelImpl(result);
  }

  private static DependencyResolutionServices extractDependencyResolutionService(DefaultVersionCatalogBuilder builder) {
    try {
      Field supplierField = DefaultVersionCatalogBuilder.class.getDeclaredField("dependencyResolutionServicesSupplier");
      supplierField.setAccessible(true);
      Object supplier = supplierField.get(builder);
      Method getMethod = supplier.getClass().getMethod("get");
      getMethod.setAccessible(true);
      Object result = getMethod.invoke(supplier);
      if (result instanceof DependencyResolutionServices) {
        return (DependencyResolutionServices)result;
      } else {
        return null;
      }
    }
    catch (NoSuchFieldException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  @Override
  public boolean canBuild(String modelName) {
    return VersionCatalogsModel.class.getName().equals(modelName) && GradleVersionUtil.isCurrentGradleAtLeast("7.0");
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.VERSION_CATALOG_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project version catalogs inspection failure")
      .withText("Unable to obtain version catalogs sources")
      .withException(exception)
      .reportMessage(project);
  }
}
