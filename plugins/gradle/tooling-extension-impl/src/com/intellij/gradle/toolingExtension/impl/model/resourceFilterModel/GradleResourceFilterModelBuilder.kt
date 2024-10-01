// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel

import com.google.gson.GsonBuilder
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.util.ReflectionUtilRt
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.file.ContentFilterable
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.internal.file.copy.RegExpNameMapper
import org.gradle.api.internal.file.copy.RenamingCopyAction
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import java.util.*
import java.util.function.Function
import java.util.regex.Matcher
import java.util.regex.Pattern

@ApiStatus.Internal
object GradleResourceFilterModelBuilder {
  @JvmStatic
  fun getIncludes(project: Project, taskName: String): MutableSet<String?> {
    val filterableTask = project.getTasks().findByName(taskName)
    if (filterableTask is PatternFilterable) {
      return (filterableTask as PatternFilterable).getIncludes()
    }

    return LinkedHashSet<String?>()
  }

  @JvmStatic
  fun getExcludes(project: Project, taskName: String): MutableSet<String?> {
    val filterableTask = project.getTasks().findByName(taskName)
    if (filterableTask is PatternFilterable) {
      return (filterableTask as PatternFilterable).getExcludes()
    }

    return LinkedHashSet<String?>()
  }

  @JvmStatic
  fun getFilters(project: Project, context: ModelBuilderContext, taskName: String): MutableList<DefaultExternalFilter?> {
    val filterReaders = ArrayList<DefaultExternalFilter?>()
    if (StringGroovyMethods.toBoolean(System.getProperty("idea.disable.gradle.resource.filtering", "false"))) {
      return filterReaders
    }

    val filterableTask = project.getTasks().findByName(taskName)
    if (filterableTask is ContentFilterable &&
        !DefaultGroovyMethods.getMetaClass(filterableTask).respondsTo(filterableTask, "getMainSpec").isEmpty()
    ) {
      val mainSpec = DefaultGroovyMethods.invokeMethod(filterableTask, "getMainSpec", arrayOfNulls<Any>(0))
      if (mainSpec == null) {
        return filterReaders
      }

      var copyActions: Any? = null
      val allCopyActionsProp = DefaultGroovyMethods.hasProperty(mainSpec, "allCopyActions")
      val copyActionsProp = DefaultGroovyMethods.hasProperty(mainSpec, "copyActions")
      if (allCopyActionsProp != null) {
        copyActions = allCopyActionsProp.getProperty(mainSpec)
      }
      else if (copyActionsProp != null) {
        copyActions = copyActionsProp.getProperty(mainSpec)
      }

      if (copyActions == null) {
        return filterReaders
      }

      if (copyActions is Iterable<*>) {
        val copyActionsIterable = copyActions
        for (o in copyActionsIterable) {
          if (o is Action<*>) {
            val action = o
            val filter = GradleResourceFilterModelBuilder.getFilter(project, context, action)
            if (filter != null) {
              filterReaders.add(filter)
            }
          }
        }
      }
    }

    return filterReaders
  }

  private fun getFilter(project: Project, context: ModelBuilderContext, action: Action<in FileCopyDetails?>): DefaultExternalFilter? {
    try {
      if ("RenamingCopyAction" == action.javaClass.getSimpleName()) {
        return getRenamingCopyFilter(action)
      }
      else {
        return getCommonFilter(action)
      }
    }
    catch (ignored: Exception) {
      context.getMessageReporter().createMessage().withGroup(Messages.RESOURCE_FILTER_MODEL_GROUP)
        .withTitle("Resource configuration warning").withText("Cannot resolve resource filtering of " +
                                                              action.javaClass.getSimpleName() +
                                                              ". " +
                                                              "IDEA may fail to build project. " +
                                                              "Consider using delegated build (enabled by default).")
        .withKind(Message.Kind.WARNING).reportMessage(project)
    }

    return null
  }

  private fun getCommonFilter(action: Action<in FileCopyDetails?>): DefaultExternalFilter {
    val filterClass = GradleResourceFilterModelBuilder.findPropertyWithType<Class<*>?>(action, Class::class.java, "filterType",
                                                                                       "val\$filterType", "arg$2", "arg$1")
    requireNotNull(filterClass) { "Unsupported action found: " + action.javaClass.getName() }


    val filterType = filterClass.getName()
    var properties = GradleResourceFilterModelBuilder.findPropertyWithType<MutableMap<*, *>?>(action, MutableMap::class.java, "properties",
                                                                                              "val\$properties", "arg$1")
    if ("org.apache.tools.ant.filters.ExpandProperties" == filterType) {
      if (properties != null && properties.get("project") != null) {
        val  /*org.apache.tools.ant.Project*/project = properties.get("project")
        properties = DefaultGroovyMethods.invokeMethod(project, "getProperties", arrayOfNulls<Any>(0)) as MutableMap<*, *>?
      }
    }


    val filter = DefaultExternalFilter()
    filter.setFilterType(filterType)
    if (properties != null) {
      filter.setPropertiesAsJsonMap(GsonBuilder().create().toJson(properties))
    }

    return filter
  }

  private fun getRenamingCopyFilter(action: Action<in FileCopyDetails?>): DefaultExternalFilter? {
    assert("RenamingCopyAction" == action.javaClass.getSimpleName())


    //Object transformer = action.transformer;
    val transformerObject = ReflectionUtilRt.getField<Any?>(RenamingCopyAction::class.java, action, null, "transformer")
    if (transformerObject !is RegExpNameMapper) {
      return null
    }

    val transformer = transformerObject

    var pattern = Optional.ofNullable<Matcher?>(
      ReflectionUtilRt.getField<Matcher?>(RegExpNameMapper::class.java, transformer, Matcher::class.java, "matcher"))
      .map<Pattern?>(Function { obj: Matcher? -> obj!!.pattern() }).map<String?>(Function { obj: Pattern? -> obj!!.pattern() })

    if (!pattern.isPresent()) {
      pattern = Optional.ofNullable<Pattern?>(
        ReflectionUtilRt.getField<Pattern?>(RegExpNameMapper::class.java, transformer, Pattern::class.java, "pattern"))
        .map<String?>(Function { obj: Pattern? -> obj!!.pattern() })
    }

    val replacement = ReflectionUtilRt.getField<String?>(RegExpNameMapper::class.java, transformer, String::class.java, "replacement")

    val filter = DefaultExternalFilter()
    filter.setFilterType("RenamingCopyFilter")
    val map = LinkedHashMap<String?, Any?>(2)
    map.put("pattern", pattern.orElse(null))
    map.put("replacement", replacement)
    filter.setPropertiesAsJsonMap(GsonBuilder().create().toJson(map))
    return filter
  }

  fun <T> findPropertyWithType(self: Any, type: Class<T?>, vararg propertyNames: String): T? {
    for (name in propertyNames) {
      try {
        val field = self.javaClass.getDeclaredField(name)
        if (type.isAssignableFrom(field.getType())) {
          field.setAccessible(true)
          return field.get(self) as T?
        }
      }
      catch (ignored: NoSuchFieldException) {
      }
      catch (ignored: IllegalAccessException) {
      }
    }

    return null
  }
}
