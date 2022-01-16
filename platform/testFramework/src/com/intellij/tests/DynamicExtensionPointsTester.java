// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestApplicationManagerKt;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.Function;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DynamicExtensionPointsTester {
  public static final Set<String> EXTENSION_POINTS_WHITE_LIST = Collections.emptySet();

  public static void checkDynamicExtensionPoints(Function<? super String, String> namer) {
    Application app = ApplicationManager.getApplication();
    if (app == null) {
      return;
    }

    app.invokeAndWait(() -> PlatformTestUtil.cleanupAllProjects());

    Map<ExtensionPointImpl<?>, Collection<WeakReference<Object>>> extensionPointToNonPlatformExtensions = collectDynamicNonPlatformExtensions(app);
    IdeaPluginDescriptor corePlugin = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID);
    assert corePlugin != null;
    try {
      // It doesn't matter what plugin to pass here. Main thing here is firing events.
      fireBeforePluginUnloadEvent(corePlugin);
      app.invokeAndWait(() -> {
        // obey contract that used by DynamicPluginManager - reloading of plugin is done in EDT and write action.
        // one write action for all - as DynamicPluginManager does (plugin unloaded in one write action)
        app.runWriteAction(() -> {
          for (ExtensionPointImpl<?> ep : extensionPointToNonPlatformExtensions.keySet()) {
            ep.reset();
          }
        });
      });
      for (Project project : ProjectUtil.getOpenProjects()) {
        ((CachedValuesManagerImpl)CachedValuesManager.getManager(project)).clearCachedValues();
      }
    }
    finally {
      firePluginUnloadedEvent(corePlugin);
    }

    GCUtil.tryGcSoftlyReachableObjects();
    //noinspection CallToSystemGC
    System.gc();
    //noinspection CallToSystemGC
    System.gc();
    String heapDump = TestApplicationManagerKt.publishHeapDump("dynamicExtension");

    AtomicBoolean failed = new AtomicBoolean(false);
    extensionPointToNonPlatformExtensions.forEach((ep, references) -> {
      String testName = escape(namer.fun("Dynamic EP unloading " + ep.getName()));
      System.out.printf("##teamcity[testStarted name='%s']%n", testName);
      System.out.flush();

      List<Object> alive = ContainerUtil.mapNotNull(references, WeakReference::get);
      if (!alive.isEmpty()) {
        String aliveExtensions = StringUtil.join(alive, o -> o + " (" + o.getClass() + ")", "\n");
        System.out.printf("##teamcity[%s name='%s' message='%s']%n", MapSerializerUtil.TEST_FAILED, testName,
                          escape("Not unloaded extensions:\n" + aliveExtensions + "\n\n" + "See testDynamicExtensions output to find a heapDump"));
        System.out.flush();
        failed.set(true);
      }
      else {
        System.out.printf("##teamcity[testFinished name='%s']%n", testName);
        System.out.flush();
      }
    });

    if (failed.get()) {
      TestCase.fail("Some of dynamic extensions have not been unloaded. See individual tests for details. Heap dump: " + heapDump);
    }
  }

  private static void fireBeforePluginUnloadEvent(@NotNull IdeaPluginDescriptor plugin) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(plugin, false);
    });
  }

  private static void firePluginUnloadedEvent(@NotNull IdeaPluginDescriptor plugin) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(plugin, false);
    });
  }

  private static @NotNull String escape(String s) {
    return MapSerializerUtil.escapeStr(s, MapSerializerUtil.STD_ESCAPER);
  }

  private static @NotNull Map<ExtensionPointImpl<?>, Collection<WeakReference<Object>>> collectDynamicNonPlatformExtensions(@NotNull Application app) {
    boolean useWhiteList = !SystemProperties.getBooleanProperty("intellij.test.all.dynamic.extension.points", false);
    Map<ExtensionPointImpl<?>, Collection<WeakReference<Object>>> extensions = new HashMap<>();
    for (Project project : ProjectUtil.getOpenProjects()) {
      collectForArea((ExtensionsAreaImpl)project.getExtensionArea(), useWhiteList, extensions);
    }
    collectForArea((ExtensionsAreaImpl)app.getExtensionArea(), useWhiteList, extensions);
    return extensions;
  }

  private static void collectForArea(@NotNull ExtensionsAreaImpl area,
                                     boolean useWhiteList,
                                     @NotNull Map<ExtensionPointImpl<?>, Collection<WeakReference<Object>>> extensions) {
    area.processExtensionPoints(ep -> {
      if (!ep.isDynamic() || (useWhiteList && !EXTENSION_POINTS_WHITE_LIST.contains(ep.getName()))) {
        return;
      }

      List<WeakReference<Object>> list = new ArrayList<>();
      ep.processWithPluginDescriptor(false, (object, pluginDescriptor) -> {
        if (!PluginManagerCore.CORE_ID.equals(pluginDescriptor.getPluginId())) {
          list.add(new WeakReference<>(object));
        }
      });
      extensions.put(ep, list);
    });
  }
}
