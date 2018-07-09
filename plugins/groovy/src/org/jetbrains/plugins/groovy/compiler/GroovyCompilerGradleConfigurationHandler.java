/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfiguration;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class GroovyCompilerGradleConfigurationHandler implements ConfigurationHandler {
  @Override
  public void apply(@NotNull Project project,
                    @NotNull IdeModifiableModelsProvider modelsProvider,
                    @NotNull ConfigurationData configuration) {
    Object obj = configuration.find("groovyCompiler");
    if (!(obj instanceof Map)) return;
    Map configurationMap = ((Map)obj);

    final GroovyCompilerConfiguration compilerConfiguration = GroovyCompilerConfiguration.getInstance(project);

    asString(configurationMap.get("heapSize"), (String heapSize) -> compilerConfiguration.setHeapSize(heapSize));

    final ExcludedEntriesConfiguration excludesConfig = (ExcludedEntriesConfiguration)compilerConfiguration.getExcludeFromStubGeneration();
    asList(configurationMap.get("excludes"), list -> {
      for (Object o : list) {
        asMap(o, map -> {
          final Object fileUrl = map.get("url");
          final Object includeSubdirectories = map.get("includeSubdirectories");
          final Object isFile = map.get("isFile");

          if ((fileUrl instanceof String)
            && (includeSubdirectories instanceof Boolean)
            && (isFile instanceof Boolean)) {
            excludesConfig.addExcludeEntryDescription(new ExcludeEntryDescription((String)fileUrl,
                                                                                  (Boolean)includeSubdirectories,
                                                                                  (Boolean)isFile,
                                                                                  excludesConfig));
          }
        });
      }
    });
  }

  private static void asString(Object value, Consumer<String> consumer) {
    if (value instanceof String) {
      consumer.consume((String)value);
    }
  }

  private static void asList(Object value, Consumer<List> consumer) {
    if (value instanceof List) {
      consumer.consume((List)value);
    }
  }

  private static void asMap(Object value, Consumer<Map> consumer) {
    if (value instanceof Map) {
      consumer.consume((Map)value);
    }
  }
}
