// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel

import com.google.gson.GsonBuilder
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ContentFilterable
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

@ApiStatus.Internal
class GradleResourceFilterModelBuilder {

  static Set<String> getIncludes(Project project, String taskName) {
    def filterableTask = project.tasks.findByName(taskName)
    if (filterableTask instanceof PatternFilterable) {
      return filterableTask.includes
    }
    return new LinkedHashSet<String>()
  }

  static Set<String> getExcludes(Project project, String taskName) {
    def filterableTask = project.tasks.findByName(taskName)
    if (filterableTask instanceof PatternFilterable) {
      return filterableTask.excludes
    }
    return new LinkedHashSet<String>()
  }

  @CompileDynamic
  static List<DefaultExternalFilter> getFilters(Project project, ModelBuilderContext context, String taskName) {
    def filterReaders = new ArrayList<DefaultExternalFilter>()
    if (System.getProperty('idea.disable.gradle.resource.filtering', 'false').toBoolean()) {
      return filterReaders
    }
    def filterableTask = project.tasks.findByName(taskName)
    if (filterableTask instanceof ContentFilterable && filterableTask.metaClass.respondsTo(filterableTask, "getMainSpec")) {
      //noinspection GrUnresolvedAccess
      def mainSpec = filterableTask.getMainSpec()
      if (mainSpec == null) {
        return filterReaders
      }
      Object copyActions = null
      if (mainSpec.hasProperty("allCopyActions")) {
        //noinspection GrUnresolvedAccess
        copyActions = mainSpec.allCopyActions
      }
      else if (mainSpec.hasProperty("copyActions")) {
        //noinspection GrUnresolvedAccess
        copyActions = mainSpec.copyActions
      }
      if (copyActions == null) {
        return filterReaders
      }
      copyActions.each { Action<? super FileCopyDetails> action ->
        def filter = getFilter(project, context, action)
        if (filter != null) {
          filterReaders.add(filter)
        }
      }
    }
    return filterReaders
  }

  private static DefaultExternalFilter getFilter(Project project, ModelBuilderContext context, Action<? super FileCopyDetails> action) {
    try {
      if ('RenamingCopyAction' == action.class.simpleName) {
        return getRenamingCopyFilter(action)
      }
      else {
        return getCommonFilter(action)
      }
    }
    catch (Exception ignored) {
      context.getMessageReporter().createMessage()
        .withGroup(Messages.RESOURCE_FILTER_MODEL_GROUP)
        .withTitle("Resource configuration warning")
        .withText(
          "Cannot resolve resource filtering of " + action.class.simpleName + ". " +
          "IDEA may fail to build project. " +
          "Consider using delegated build (enabled by default)."
        )
        .withKind(Message.Kind.WARNING)
        .reportMessage(project)
    }
    return null
  }

  @CompileDynamic
  private static DefaultExternalFilter getCommonFilter(Action<? super FileCopyDetails> action) {
    def filterClass = findPropertyWithType(action, Class, 'filterType', 'val$filterType', 'arg$2', 'arg$1')
    if (filterClass == null) {
      throw new IllegalArgumentException("Unsupported action found: " + action.class.name)
    }

    def filterType = filterClass.name
    def properties = findPropertyWithType(action, Map, 'properties', 'val$properties', 'arg$1')
    if ('org.apache.tools.ant.filters.ExpandProperties' == filterType) {
      if (properties != null && properties['project'] != null) {
        properties = properties['project'].properties
      }
    }

    def filter = new DefaultExternalFilter()
    filter.filterType = filterType
    if (properties != null) {
      filter.propertiesAsJsonMap = new GsonBuilder().create().toJson(properties)
    }
    return filter
  }

  @CompileDynamic
  private static DefaultExternalFilter getRenamingCopyFilter(Action<? super FileCopyDetails> action) {
    assert 'RenamingCopyAction' == action.class.simpleName

    //noinspection GrUnresolvedAccess
    def transformer = action.transformer
    def pattern = transformer.matcher?.pattern()?.pattern() ?:
                  transformer.pattern?.pattern()
    def replacement = transformer.replacement

    def filter = new DefaultExternalFilter()
    filter.filterType = 'RenamingCopyFilter'
    filter.propertiesAsJsonMap = new GsonBuilder().create().toJson([pattern: pattern, replacement: replacement])
    return filter
  }

  static <T> T findPropertyWithType(Object self, Class<T> type, String... propertyNames) {
    for (String name in propertyNames) {
      try {
        def field = self.class.getDeclaredField(name)
        if (field != null && type.isAssignableFrom(field.type)) {
          field.setAccessible(true)
          return field.get(self) as T
        }
      }
      catch (NoSuchFieldException ignored) {
      }
    }
    return null
  }
}
