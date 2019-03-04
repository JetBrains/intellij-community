// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ProhibitAWTEvents;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.BackgroundableDataProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.ide.impl.DataManagerImpl.getDataProviderEx;

class AsyncDataContext extends DataManagerImpl.MyDataContext {
  private static final Logger LOG = Logger.getInstance(AsyncDataContext.class);
  private final List<WeakReference<Component>> myHierarchy;
  @SuppressWarnings("deprecation")
  private final Map<Component, DataProvider> myProviders = new ConcurrentFactoryMap<Component, DataProvider>() {
    @Nullable
    @Override
    protected DataProvider create(Component key) {
      return ActionUpdateEdtExecutor.computeOnEdt(() -> {
        DataProvider provider = getDataProviderEx(key);
        if (provider == null) return null;

        if (provider instanceof BackgroundableDataProvider) {
          return ((BackgroundableDataProvider)provider).createBackgroundDataProvider();
        }
        return dataKey -> {
          boolean bg = !ApplicationManager.getApplication().isDispatchThread();
          return ActionUpdateEdtExecutor.computeOnEdt(() -> {
            long start = System.currentTimeMillis();
            try {
              return provider.getData(dataKey);
            }
            finally {
              long elapsed = System.currentTimeMillis() - start;
              if (elapsed > 100 && bg) {
                LOG.warn("Slow data provider " + provider + " took " + elapsed + "ms on " + dataKey +
                         ". Consider speeding it up and/or implementing BackgroundableDataProvider.");
              }
            }
          });
        };
      });
    }

    @NotNull
    @Override
    protected ConcurrentMap<Component, DataProvider> createMap() {
      return ContainerUtil.createConcurrentWeakKeySoftValueMap();
    }
  };

  AsyncDataContext(DataContext syncContext) {
    super(syncContext.getData(PlatformDataKeys.CONTEXT_COMPONENT));
    ApplicationManager.getApplication().assertIsDispatchThread();
    Component component = getData(PlatformDataKeys.CONTEXT_COMPONENT);
    List<Component> hierarchy = JBIterable.generate(component, Component::getParent).toList();
    for (Component each : hierarchy) {
      myProviders.get(each);
    }
    myHierarchy = ContainerUtil.map(hierarchy, WeakReference::new);
  }

  @Override
  protected Object calcData(@NotNull String dataId, Component focused) {
    try (AccessToken ignored = ProhibitAWTEvents.start("getData")) {
      for (WeakReference<Component> reference : myHierarchy) {
        Component component = SoftReference.dereference(reference);
        if (component == null) continue;
        DataProvider dataProvider = myProviders.get(component);
        if (dataProvider == null) continue;
        Object data = ((DataManagerImpl)DataManager.getInstance()).getDataFromProvider(dataProvider, dataId, null);
        if (data != null) return data;
      }
    }
    return null;
  }

}
