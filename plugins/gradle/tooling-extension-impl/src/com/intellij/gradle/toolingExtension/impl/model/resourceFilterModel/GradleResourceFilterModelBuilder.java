// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel;

import com.google.gson.GsonBuilder;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.util.ReflectionUtilRt;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.internal.file.copy.RegExpNameMapper;
import org.gradle.api.internal.file.copy.RenamingCopyAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApiStatus.Internal
public class GradleResourceFilterModelBuilder {
  public static Set<String> getIncludes(Project project, String taskName) {
    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof PatternFilterable) {
      return ((PatternFilterable)filterableTask).getIncludes();
    }

    return new LinkedHashSet<>();
  }

  public static Set<String> getExcludes(Project project, String taskName) {
    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof PatternFilterable) {
      return ((PatternFilterable)filterableTask).getExcludes();
    }

    return new LinkedHashSet<>();
  }

  public static List<DefaultExternalFilter> getFilters(final Project project, final ModelBuilderContext context, String taskName) {
    final ArrayList<DefaultExternalFilter> filterReaders = new ArrayList<>();
    if (StringGroovyMethods.toBoolean(System.getProperty("idea.disable.gradle.resource.filtering", "false"))) {
      return filterReaders;
    }

    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof ContentFilterable &&
        !DefaultGroovyMethods.getMetaClass(filterableTask).respondsTo(filterableTask, "getMainSpec").isEmpty()) {
      //noinspection GrUnresolvedAccess
      Object mainSpec = DefaultGroovyMethods.invokeMethod(filterableTask, "getMainSpec", new Object[0]);
      if (mainSpec == null) {
        return filterReaders;
      }

      Object copyActions = null;
      Map specProperties = DefaultGroovyMethods.getProperties(mainSpec);
      if (DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(mainSpec, "allCopyActions"))) {
        //noinspection GrUnresolvedAccess
        copyActions = specProperties.get("allCopyActions");
      }
      else if (DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(mainSpec, "copyActions"))) {
        //noinspection GrUnresolvedAccess
        copyActions = specProperties.get("copyActions");
      }

      if (copyActions == null) {
        return filterReaders;
      }

      if (copyActions instanceof Iterable) {
        Iterable copyActionsIterable = (Iterable)copyActions;
        for (Object o : copyActionsIterable) {
          if (o instanceof Action) {
            Action action = (Action)o;
            DefaultExternalFilter filter = getFilter(project, context, action);
            if (filter != null) {
              filterReaders.add(filter);
            }
          }
        }
      }
    }

    return filterReaders;
  }

  private static DefaultExternalFilter getFilter(Project project, ModelBuilderContext context, Action<? super FileCopyDetails> action) {
    try {
      if ("RenamingCopyAction".equals(action.getClass().getSimpleName())) {
        return getRenamingCopyFilter(action);
      }
      else {
        return getCommonFilter(action);
      }
    }
    catch (Exception ignored) {
      context.getMessageReporter().createMessage().withGroup(Messages.RESOURCE_FILTER_MODEL_GROUP)
        .withTitle("Resource configuration warning").withText("Cannot resolve resource filtering of " +
                                                              action.getClass().getSimpleName() +
                                                              ". " +
                                                              "IDEA may fail to build project. " +
                                                              "Consider using delegated build (enabled by default).")
        .withKind(Message.Kind.WARNING).reportMessage(project);
    }

    return null;
  }

  private static DefaultExternalFilter getCommonFilter(Action<? super FileCopyDetails> action) {
    Class filterClass = findPropertyWithType(action, Class.class, "filterType", "val$filterType", "arg$2", "arg$1");
    if (filterClass == null) {
      throw new IllegalArgumentException("Unsupported action found: " + action.getClass().getName());
    }


    String filterType = filterClass.getName();
    Map properties = findPropertyWithType(action, Map.class, "properties", "val$properties", "arg$1");
    if ("org.apache.tools.ant.filters.ExpandProperties".equals(filterType)) {
      if (properties != null && properties.get("project") != null) {
        properties = DefaultGroovyMethods.getProperties(properties.get("project"));
      }
    }


    DefaultExternalFilter filter = new DefaultExternalFilter();
    filter.setFilterType(filterType);
    if (properties != null) {
      filter.setPropertiesAsJsonMap(new GsonBuilder().create().toJson(properties));
    }

    return filter;
  }

  private static DefaultExternalFilter getRenamingCopyFilter(Action<? super FileCopyDetails> action) {
    assert "RenamingCopyAction".equals(action.getClass().getSimpleName());

    //noinspection GrUnresolvedAccess
    //Object transformer = action.transformer;
    Object transformerObject = ReflectionUtilRt.getField(RenamingCopyAction.class, action, null, "transformer");
    if (!(transformerObject instanceof RegExpNameMapper)) {
      return null;
    }

    RegExpNameMapper transformer = (RegExpNameMapper)transformerObject;

    Optional<String> pattern
      = Optional.ofNullable(ReflectionUtilRt.getField(RegExpNameMapper.class, transformer, Matcher.class, "matcher"))
        .map(Matcher::pattern).map(Pattern::pattern);

    if (!pattern.isPresent()) {
      pattern = Optional.ofNullable(ReflectionUtilRt.getField(RegExpNameMapper.class, transformer, Pattern.class, "pattern"))
        .map(Pattern::pattern);
    }

    String replacement = ReflectionUtilRt.getField(RegExpNameMapper.class, transformer, String.class, "replacement");

    DefaultExternalFilter filter = new DefaultExternalFilter();
    filter.setFilterType("RenamingCopyFilter");
    LinkedHashMap<String, Object> map = new LinkedHashMap<>(2);
    map.put("pattern", pattern.orElse(null));
    map.put("replacement", replacement);
    filter.setPropertiesAsJsonMap(new GsonBuilder().create().toJson(map));
    return filter;
  }

  public static <T> T findPropertyWithType(Object self, Class<T> type, String... propertyNames) {
    for (String name : propertyNames) {
      try {
        Field field = self.getClass().getDeclaredField(name);
        if (type.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          return (T)field.get(self);
        }
      }
      catch (NoSuchFieldException | IllegalAccessException ignored) {
      }
    }

    return null;
  }
}
