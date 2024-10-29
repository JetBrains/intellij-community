// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel

import com.google.gson.GsonBuilder
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.util.ReflectionUtilRt.getField
import org.codehaus.groovy.runtime.DefaultGroovyMethods.*
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ContentFilterable
import org.gradle.api.internal.file.copy.RegExpNameMapper
import org.gradle.api.internal.file.copy.RenamingCopyAction
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.util.regex.Matcher
import java.util.regex.Pattern

@ApiStatus.Internal
object GradleResourceFilterModelBuilder {
  @JvmStatic
  fun getIncludes(project: Project, taskName: String): MutableSet<String?> {
    val filterableTask = project.tasks.findByName(taskName)
    if (filterableTask is PatternFilterable) {
      return filterableTask.includes
    }

    return LinkedHashSet<String?>()
  }

  @JvmStatic
  fun getExcludes(project: Project, taskName: String): Set<String> {
    val filterableTask = project.tasks.findByName(taskName)
    if (filterableTask is PatternFilterable) {
      return filterableTask.excludes
    }

    return LinkedHashSet<String>()
  }

  @JvmStatic
  fun getFilters(project: Project, context: ModelBuilderContext, taskName: String): List<DefaultExternalFilter> {
    val filterReaders = ArrayList<DefaultExternalFilter>()
    if (System.getProperty("idea.disable.gradle.resource.filtering").toBoolean()) {
      return filterReaders
    }

    val filterableTask = project.tasks.findByName(taskName)
    if (filterableTask is ContentFilterable &&
        !getMetaClass(filterableTask).respondsTo(filterableTask, "getMainSpec").isEmpty()
    ) {
      val mainSpec = invokeMethod(filterableTask, "getMainSpec", arrayOfNulls<Any>(0))
      if (mainSpec == null) {
        return filterReaders
      }

      var copyActions: Any? = null
      val property = hasProperty(mainSpec, "allCopyActions") ?: hasProperty(mainSpec, "copyActions") ?: return filterReaders
      copyActions = property.getProperty(mainSpec) ?: return filterReaders

      if (copyActions is Iterable<*>) {
        for (action in copyActions) {
          if (action is Action<*>) {
            val filter = getFilter(project, context, action)
            if (filter != null) {
              filterReaders.add(filter)
            }
          }
        }
      }
    }

    return filterReaders
  }

  private fun getFilter(project: Project, context: ModelBuilderContext, action: Action<*>): DefaultExternalFilter? {
    try {
      if ("RenamingCopyAction" == action.javaClass.getSimpleName()) {
        return getRenamingCopyFilter(action)
      }
      else {
        return getCommonFilter(action)
      }
    }
    catch (_: Exception) {
      context.getMessageReporter()
        .createMessage()
        .withGroup(Messages.RESOURCE_FILTER_MODEL_GROUP)
        .withTitle("Resource configuration warning")
        .withText("""
          | Cannot resolve resource filtering of ${action.javaClass.getSimpleName()}.
          | IDEA may fail to build project. Consider using delegated build (enabled by default).
          | """.trimMargin())
        .withKind(Message.Kind.WARNING)
        .reportMessage(project)
    }

    return null
  }

  private fun getCommonFilter(action: Action<*>): DefaultExternalFilter {
    val filterClass = findPropertyWithType<Class<*>>(action, Class::class.java, "filterType",
                                                     "val\$filterType", "arg$2", "arg$1")
    requireNotNull(filterClass) { "Unsupported action found: " + action.javaClass.getName() }


    val filterType = filterClass.getName()
    var properties: java.util.Map<*,*>? = findPropertyWithType<java.util.Map<*,*>>(action, java.util.Map::class.java, "properties",
                                                                                   "val\$properties", "arg$1")
    if ("org.apache.tools.ant.filters.ExpandProperties" == filterType) {
      if (properties != null && properties.get("project") != null) {
        val  /*org.apache.tools.ant.Project*/ project = properties.get("project")
        properties = invokeMethod(project, "getProperties", arrayOfNulls<Any>(0)) as java.util.Map<*, *>?
      }
    }


    val filter = DefaultExternalFilter()
    filter.filterType = filterType
    if (properties != null) {
      filter.propertiesAsJsonMap = GsonBuilder().create().toJson(properties)
    }

    return filter
  }

  private fun getRenamingCopyFilter(action: Action<*>): DefaultExternalFilter? {
    assert("RenamingCopyAction" == action.javaClass.getSimpleName())

    val transformerObject = getField<Any?>(RenamingCopyAction::class.java, action, null, "transformer")
    if (transformerObject !is RegExpNameMapper) {
      return null
    }

    val transformer = transformerObject

    var pattern = getField<Matcher?>(RegExpNameMapper::class.java, transformer, Matcher::class.java, "matcher")?.pattern()?.pattern()

    if (pattern == null) {
      pattern = getField<Pattern?>(RegExpNameMapper::class.java, transformer, Pattern::class.java, "pattern")?.pattern()
    }

    val replacement = getField<String?>(RegExpNameMapper::class.java, transformer, String::class.java, "replacement")

    val filter = DefaultExternalFilter()
    filter.filterType = "RenamingCopyFilter"
    val map = LinkedHashMap<String?, Any?>(2)
    map.put("pattern", pattern)
    map.put("replacement", replacement)
    filter.propertiesAsJsonMap = GsonBuilder().create().toJson(map)
    return filter
  }

  fun <T> findPropertyWithType(self: Any, type: Class<T>, vararg propertyNames: String): T? {
    for (name in propertyNames) {
      try {
        val field = self.javaClass.getDeclaredField(name)
        if (type.isAssignableFrom(field.type)) {
          field.setAccessible(true)
          @Suppress("UNCHECKED_CAST")
          return field.get(self) as T?
        }
      }
      catch (_: NoSuchFieldException) {
      }
      catch (_: IllegalAccessException) {
      }
    }

    return null
  }
}
