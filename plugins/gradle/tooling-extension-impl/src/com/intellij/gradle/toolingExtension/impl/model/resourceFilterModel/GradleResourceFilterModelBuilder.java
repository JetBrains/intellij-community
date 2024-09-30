// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.resourceFilterModel;

import com.google.gson.GsonBuilder;
import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.tasks.util.PatternFilterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.model.DefaultExternalFilter;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

import java.lang.reflect.Field;
import java.util.*;

@ApiStatus.Internal
public class GradleResourceFilterModelBuilder {
  public static Set<String> getIncludes(Project project, String taskName) {
    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof PatternFilterable) {
      return ((PatternFilterable)filterableTask).getIncludes();
    }

    return new LinkedHashSet<String>();
  }

  public static Set<String> getExcludes(Project project, String taskName) {
    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof PatternFilterable) {
      return ((PatternFilterable)filterableTask).getExcludes();
    }

    return new LinkedHashSet<String>();
  }

  public static List<DefaultExternalFilter> getFilters(final Project project, final ModelBuilderContext context, String taskName) {
    final ArrayList<DefaultExternalFilter> filterReaders = new ArrayList<DefaultExternalFilter>();
    if (StringGroovyMethods.toBoolean(System.getProperty("idea.disable.gradle.resource.filtering", "false"))) {
      return ((List<DefaultExternalFilter>)(filterReaders));
    }

    Task filterableTask = project.getTasks().findByName(taskName);
    if (filterableTask instanceof ContentFilterable &&
        DefaultGroovyMethods.getMetaClass(filterableTask).respondsTo(filterableTask, "getMainSpec")) {
      //noinspection GrUnresolvedAccess
      Object mainSpec = DefaultGroovyMethods.invokeMethod(filterableTask, "getMainSpec", new Object[0]);
      if (mainSpec == null) {
        return ((List<DefaultExternalFilter>)(filterReaders));
      }

      Object copyActions = null;
      if (DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(mainSpec, "allCopyActions"))) {
        //noinspection GrUnresolvedAccess
        copyActions = mainSpec.allCopyActions;
      }
      else if (DefaultGroovyMethods.asBoolean(DefaultGroovyMethods.hasProperty(mainSpec, "copyActions"))) {
        //noinspection GrUnresolvedAccess
        copyActions = mainSpec.copyActions;
      }

      if (copyActions == null) {
        return ((List<DefaultExternalFilter>)(filterReaders));
      }

      DefaultGroovyMethods.each(copyActions, new Closure<Boolean>(null, null) {
        public Boolean doCall(Action<? super FileCopyDetails> action) {
          DefaultExternalFilter filter = getFilter(project, context, action);
          if (filter != null) {
            return filterReaders.add(filter);
          }
        }
      });
    }

    return ((List<DefaultExternalFilter>)(filterReaders));
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

    return ((DefaultExternalFilter)(filter));
  }

  private static DefaultExternalFilter getRenamingCopyFilter(Action<? super FileCopyDetails> action) {
    assert "RenamingCopyAction".equals(action.getClass().getSimpleName());

    //noinspection GrUnresolvedAccess
    Object transformer = action.transformer;
    final Object pattern1 = transformer.matcher.invokeMethod("pattern", new Object[0]).invokeMethod("pattern", new Object[0]);
    Object pattern = pattern1 ? pattern1 : transformer.pattern.invokeMethod("pattern", new Object[0]);
    Object replacement = transformer.replacement;

    DefaultExternalFilter filter = new DefaultExternalFilter();
    filter.setFilterType("RenamingCopyFilter");
    LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>(2);
    map.put("pattern", pattern);
    map.put("replacement", replacement);
    filter.setPropertiesAsJsonMap(new GsonBuilder().create().toJson(map));
    return ((DefaultExternalFilter)(filter));
  }

  public static <T> T findPropertyWithType(Object self, Class<T> type, String... propertyNames) {
    for (String name : propertyNames) {
      try {
        Field field = self.getClass().getDeclaredField(name);
        if (field != null && type.isAssignableFrom(field.getType())) {
          field.setAccessible(true);
          return DefaultGroovyMethods.asType(field.get(self), getProperty("T"));
        }
      }
      catch (NoSuchFieldException ignored) {
      }
    }

    return null;
  }
}
