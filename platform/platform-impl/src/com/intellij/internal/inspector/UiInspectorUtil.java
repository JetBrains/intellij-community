// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.inspector;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionWithDelegate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UiInspectorUtil {
  private static final String PROPERTY_KEY = "UiInspectorContextProvider.Key";

  public static void registerProvider(@NotNull JComponent component, @NotNull UiInspectorContextProvider provider) {
    component.putClientProperty(PROPERTY_KEY, provider);
  }

  public static UiInspectorContextProvider getProvider(@NotNull Object component) {
    if (component instanceof UiInspectorContextProvider) {
      return ((UiInspectorContextProvider)component);
    }
    if (component instanceof JComponent) {
      return ObjectUtils.tryCast(((JComponent)component).getClientProperty(PROPERTY_KEY), UiInspectorContextProvider.class);
    }
    return null;
  }

  @Nullable
  public static String getActionId(@NotNull AnAction action) {
    if (action instanceof CustomisedActionGroup) {
      action = ((CustomisedActionGroup)action).getOrigin();
    }
    return ActionManager.getInstance().getId(action);
  }

  @NotNull
  public static List<PropertyBean> collectActionGroupInfo(@NotNull @NonNls String prefix,
                                                          @NotNull ActionGroup group,
                                                          @Nullable String place) {
    List<PropertyBean> result = new ArrayList<>();

    if (place != null) {
      result.add(new PropertyBean(prefix + " Place", place, true));
    }

    String toolbarId = getActionId(group);
    result.add(new PropertyBean(prefix + " Group", toolbarId, true));

    Set<String> ids = new HashSet<>();
    recursiveCollectGroupIds(group, ids);
    ContainerUtil.addIfNotNull(ids, toolbarId);
    if (ids.size() > 1 ||
        ids.size() == 1 && toolbarId == null) {
      result.add(new PropertyBean("All Groups", StringUtil.join(ids, ", "), true));
    }
    return result;
  }

  @NotNull
  public static List<PropertyBean> collectAnActionInfo(@NotNull AnAction action) {
    List<PropertyBean> result = new ArrayList<>();
    result.add(new PropertyBean("Action", action.getClass().getName(), true));

    boolean isGroup = action instanceof ActionGroup;
    result.add(new PropertyBean("Action" + (isGroup ? " Group" : "") + " ID", getActionId(action), true));

    final ClassLoader classLoader = action.getClass().getClassLoader();
    if (classLoader instanceof PluginAwareClassLoader) {
      result.add(new PropertyBean("Action Plugin ID", ((PluginAwareClassLoader)classLoader).getPluginId().getIdString(), true));
    }

    if (action instanceof ActionWithDelegate<?>) {
      Object delegate = ((ActionWithDelegate<?>)action).getDelegate();
      if (delegate instanceof AnAction) {
        result.add(new PropertyBean("Action Delegate", delegate.getClass().getName(), true));
        result.add(new PropertyBean("Action Delegate ID", getActionId((AnAction)delegate), true));
      }
      result.add(new PropertyBean("Action Delegate toString", delegate, false));
    }

    return result;
  }

  private static void recursiveCollectGroupIds(@NotNull ActionGroup group, @NotNull Set<? super String> result) {
    for (AnAction action : group.getChildren(null)) {
      if (action instanceof ActionGroup) {
        ActionGroup child = (ActionGroup)action;
        ContainerUtil.addIfNotNull(result, getActionId(child));
        recursiveCollectGroupIds(child, result);
      }
    }
  }

  public static @NotNull String getComponentName(@NotNull Component component) {
    String name = getClassName(component);

    String componentName = component.getName();
    if (StringUtil.isNotEmpty(componentName)) {
      name += " \"" + componentName + "\"";
    }
    return name;
  }

  public static @NotNull String getClassName(@NotNull Object value) {
    Class<?> clazz0 = value.getClass();
    Class<?> clazz = clazz0.isAnonymousClass() ? clazz0.getSuperclass() : clazz0;
    return clazz.getSimpleName();
  }

  public static void openClassByFqn(@Nullable Project project, @NotNull String jvmFqn, boolean requestFocus) {
    PsiElement classElement = findClassByFqn(project, jvmFqn);
    if (classElement != null) {
      PsiElement navigationElement = classElement.getNavigationElement();
      if (navigationElement instanceof Navigatable) {
        ((Navigatable)navigationElement).navigate(requestFocus);
      }
      else {
        PsiNavigateUtil.navigate(classElement, requestFocus);
      }
    }
  }

  @Nullable
  public static PsiElement findClassByFqn(@Nullable Project project, @NotNull String jvmFqn) {
    if (project == null) return null;

    try {
      String javaPsiFacadeFqn = "com.intellij.psi.JavaPsiFacade";
      PluginId pluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(javaPsiFacadeFqn);
      Class<?> facade = null;
      if (pluginId != null) {
        IdeaPluginDescriptor plugin = PluginManager.getInstance().findEnabledPlugin(pluginId);
        if (plugin != null) {
          facade = Class.forName(javaPsiFacadeFqn, false, plugin.getPluginClassLoader());
        }
      }
      else {
        facade = Class.forName(javaPsiFacadeFqn);
      }
      if (facade != null) {
        Method getInstance = facade.getDeclaredMethod("getInstance", Project.class);
        Method findClass = facade.getDeclaredMethod("findClass", String.class, GlobalSearchScope.class);
        String ourFqn = jvmFqn.replace('$', '.');
        Object result = findClass.invoke(getInstance.invoke(null, project), ourFqn, GlobalSearchScope.allScope(project));
        if (result == null) {
          // if provided jvmFqn is anonymous class, try to find containing class and then find anonymous class inside
          String[] parts = jvmFqn.split("\\$\\d+");
          String containingClassJvmFqn = parts[0];
          String containingClassOurFqn = containingClassJvmFqn.replace('$', '.');
          Object containingClass = findClass.invoke(getInstance.invoke(null, project),
                                                    containingClassOurFqn, GlobalSearchScope.allScope(project));
          if (containingClass instanceof PsiElement) {
            result = findAnonymousClass((PsiElement)containingClass, jvmFqn);
            if (result == null) {
              result = containingClass;
            }
          }
        }
        if (result instanceof PsiElement) {
          return (PsiElement)result;
        }
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  @Nullable
  private static PsiElement findAnonymousClass(@NotNull PsiElement containingClass, @NotNull String jvmFqn) {
    try {
      Class<?> searchContributor = Class.forName("com.intellij.ide.actions.searcheverywhere.ClassSearchEverywhereContributor");
      Method getPathToAnonymousClass = searchContributor.getDeclaredMethod("pathToAnonymousClass", String.class);
      getPathToAnonymousClass.setAccessible(true);
      String path = (String)getPathToAnonymousClass.invoke(null, jvmFqn);
      if (path == null) {
        return null;
      }
      Method getElement = searchContributor.getDeclaredMethod("getElement", PsiElement.class, String.class);
      return (PsiElement)getElement.invoke(null, containingClass, path);
    }
    catch (Exception ignore) {
    }
    return null;
  }
}
