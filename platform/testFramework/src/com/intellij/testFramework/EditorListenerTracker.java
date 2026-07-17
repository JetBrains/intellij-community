// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TestOnly
public final class EditorListenerTracker {
  private static final Set<String> APP_LEVEL_LISTENERS = Set.of(
    "com.intellij.copyright.CopyrightManagerDocumentListener$",
    "com.intellij.model.BranchServiceImpl$",
    "com.jetbrains.liveEdit.highlighting.ElementHighlighterCaretListener",
    "com.intellij.grazie.ide.inspection.auto.ChangeTracker$",
    "com.intellij.ml.llm.nextEdits.backend.logs.statistics.components.NextEditCaretFeatures$"
  );

  private final Map<Class<? extends EventListener>, List<? extends EventListener>> before;

  public EditorListenerTracker() {
    EncodingManager.getInstance(); //adds listeners
    before = ((EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster()).getListeners();
  }

  public void checkListenersLeak() throws AssertionError {
    try {
      EditorEventMulticasterImpl multicaster = (EditorEventMulticasterImpl)EditorFactory.getInstance().getEventMulticaster();
      Map<Class<? extends EventListener>, List<? extends EventListener>> after = multicaster.getListeners();
      Map<Class<? extends EventListener>, List<? extends EventListener>> leaked = new LinkedHashMap<>();
      for (Map.Entry<Class<? extends EventListener>, List<? extends EventListener>> entry : after.entrySet()) {
        Class<? extends EventListener> aClass = entry.getKey();
        List<? extends EventListener> beforeList = before.get(aClass);
        List<EventListener> afterList = new ArrayList<>(entry.getValue());
        if (beforeList != null) {
          afterList.removeAll(beforeList);
        }
        // listeners may hang on default project which comes and goes unpredictably, so just ignore them
        afterList.removeIf(listener -> {
          if (isDefaultProjectOwned(listener)) {
            return true;
          }

          // app level listener
          String name = listener.getClass().getName();
          return ContainerUtil.exists(APP_LEVEL_LISTENERS, name::startsWith);
        });
        if (!afterList.isEmpty()) {
          leaked.put(aClass, afterList);
        }
      }

      for (Map.Entry<Class<? extends EventListener>, List<? extends EventListener>> entry : leaked.entrySet()) {
        Class<? extends EventListener> aClass = entry.getKey();
        List<? extends EventListener> list = entry.getValue();
        String projectNames =
          StringUtil.join(ContainerUtil.map(ProjectManager.getInstance().getOpenProjects(), project -> project.getName()), ", ");
        Assert.fail("Listeners leaked for " + aClass+":\n"+list + "\nOpened projects: " + projectNames);
      }
    }
    finally {
      before.clear();
    }
  }

  private static boolean isDefaultProjectOwned(EventListener listener) {
    Boolean direct = getDirectProjectOwnership(listener);
    if (direct != null) return direct;

    // It's done to filter out not only direct connections to default project,
    // but also more complicated scenarios (e.g., com.intellij.codeInsight.ExternalAnnotationsManagerImpl.MyDocumentListener).
    Object owner = getEnclosingInstance(listener);
    return owner != null && Boolean.TRUE.equals(getProjectOwnership(owner));
  }

  private static Object getEnclosingInstance(EventListener listener) {
    Class<?> aClass = listener.getClass();
    while (aClass != null) {
      try {
        Field field = aClass.getDeclaredField("this$0");
        field.setAccessible(true);
        return field.get(listener);
      }
      catch (NoSuchFieldException ignored) {
        aClass = aClass.getSuperclass();
      }
      catch (IllegalAccessException | RuntimeException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Boolean getProjectOwnership(Object object) {
    Class<?> aClass = object.getClass();
    while (aClass != null) {
      for (Field field : aClass.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        Class<?> fieldType = field.getType();
        if (!Project.class.isAssignableFrom(fieldType) &&
            !PsiManager.class.isAssignableFrom(fieldType) &&
            !PsiDocumentManagerEx.class.isAssignableFrom(fieldType)) {
          continue;
        }

        try {
          field.setAccessible(true);
          Boolean fieldOwnership = getDirectProjectOwnership(field.get(object));
          if (fieldOwnership != null) return fieldOwnership;
        }
        catch (IllegalAccessException | RuntimeException ignored) {
        }
      }
      aClass = aClass.getSuperclass();
    }
    return null;
  }

  private static Boolean getDirectProjectOwnership(Object object) {
    if (object instanceof PsiManager manager) {
      return manager.getProject().isDefault();
    }
    if (object instanceof PsiDocumentManager) {
      //noinspection CastConflictsWithInstanceof
      return ((PsiDocumentManagerEx)object).isDefaultProject();
    }
    return null;
  }
}
