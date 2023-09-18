// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel

import com.google.gson.GsonBuilder
import groovy.transform.CompileDynamic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ContentFilterable
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter
import org.jetbrains.plugins.gradle.model.ExternalFilter
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext

@ApiStatus.Internal
class GradleResourceFilterModelBuilder {

  @CompileDynamic
  static List<List> getFilters(Project project, ModelBuilderContext context, String taskName) {
    def includes = []
    def excludes = []
    def filterReaders = [] as List<ExternalFilter>
    def filterableTask = project.tasks.findByName(taskName)
    if (filterableTask instanceof PatternFilterable) {
      includes += filterableTask.includes
      excludes += filterableTask.excludes
    }

    if (System.getProperty('idea.disable.gradle.resource.filtering', 'false').toBoolean()) {
      return [includes, excludes, filterReaders]
    }

    if (filterableTask instanceof ContentFilterable && filterableTask.metaClass.respondsTo(filterableTask, "getMainSpec")) {
      //noinspection GrUnresolvedAccess
      def properties = filterableTask.getMainSpec().properties
      def copyActions = properties?.allCopyActions ?: properties?.copyActions
      copyActions?.each { Action<? super FileCopyDetails> action ->
        def filter = getFilter(project, context, action)
        if (filter != null) {
          filterReaders << filter
        }
      }
    }

    return [includes, excludes, filterReaders]
  }

  private static ExternalFilter getFilter(Project project, ModelBuilderContext context, Action<? super FileCopyDetails> action) {
    try {
      if ('RenamingCopyAction' == action.class.simpleName) {
        return getRenamingCopyFilter(action)
      }
      else {
        return getCommonFilter(action)
      }
    }
    catch (Exception ignored) {
      context.getMessageReporter().reportMessage(project, ErrorMessageBuilder.create(project, "Resource configuration errors")
        .withDescription("Cannot resolve resource filtering of " + action.class.simpleName + ". " +
                         "IDEA may fail to build project. " +
                         "Consider using delegated build (enabled by default).")
        .buildMessage())
    }
    return null
  }

  @CompileDynamic
  private static ExternalFilter getCommonFilter(Action<? super FileCopyDetails> action) {
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
  private static ExternalFilter getRenamingCopyFilter(Action<? super FileCopyDetails> action) {
    assert 'RenamingCopyAction' == action.class.simpleName

    def pattern = action.transformer.matcher?.pattern()?.pattern() ?:
                  action.transformer.pattern?.pattern()
    def replacement = action.transformer.replacement

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
