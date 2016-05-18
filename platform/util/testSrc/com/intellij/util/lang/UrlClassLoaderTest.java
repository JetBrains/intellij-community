/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.lang;

import com.intellij.openapi.application.PathManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

/**
 * @author Dmitry Avdeev
 */
public class UrlClassLoaderTest {
  @Test
  public void testBootstrapResources() {
    String name = "com/sun/xml/internal/messaging/saaj/soap/LocalStrings.properties";
    assertNotNull(UrlClassLoaderTest.class.getClassLoader().getResourceAsStream(name));
    assertNull(UrlClassLoader.build().get().getResourceAsStream(name));
    assertNotNull(UrlClassLoader.build().allowBootstrapResources().get().getResourceAsStream(name));
  }

  @Test
  public void testConcurrentResourceLoading() throws Exception {
    List<String> resourceNames = ContainerUtil.newArrayList();
    List<URL> urls = ContainerUtil.newArrayList();

    File[] libs = ObjectUtils.assertNotNull(new File(PathManager.getHomePathFor(UrlClassLoader.class) + "/lib").listFiles());
    for (File file : libs) {
      if (file.getName().endsWith(".jar")) {
        urls.add(file.toURI().toURL());

        try (ZipFile zipFile = new ZipFile(file)) {
          Enumeration<? extends ZipEntry> entries = zipFile.entries();
          while (entries.hasMoreElements()) {
            resourceNames.add(entries.nextElement().getName());
          }
        }
      }
    }

    int attemptCount = 1000;
    int threadCount = 3;
    int resourceCount = 20;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount, ConcurrencyUtil.newNamedThreadFactory("concurrent loading"));
    try {
      Random random = new Random();
      UrlClassLoader.CachePool pool = UrlClassLoader.createCachePool();
      for (int attempt = 0; attempt < attemptCount; attempt++) {
        // fails also without cache pool (but with cache enabled), but takes much longer
        UrlClassLoader loader = UrlClassLoader.build().urls(urls).parent(null).useCache(pool, (url) -> true).get();
        List<String> namesToLoad = ContainerUtil.newArrayList();
        for (int j = 0; j < resourceCount; j++) {
          namesToLoad.add(resourceNames.get(random.nextInt(resourceNames.size())));
        }

        List<Future> futures = ContainerUtil.newArrayList();
        for (int i = 0; i < threadCount; i++) {
          futures.add(executor.submit(() -> {
            for (String name : namesToLoad) {
              try {
                assertNotNull(findResource(loader, name, random.nextBoolean()));
              }
              catch (Throwable e) {
                System.out.println("Failed loading " + name);
                throw new RuntimeException(e);
              }
            }
          }));
        }

        for (Future future : futures) {
          future.get();
        }
      }
    }
    finally {
      executor.shutdownNow();
      executor.awaitTermination(1000, TimeUnit.SECONDS);
    }
  }

  private static URL findResource(UrlClassLoader loader, String name, boolean findAll) {
    if (findAll) {
      try {
        Enumeration<URL> resources = loader.getResources(name);
        assertTrue(resources.hasMoreElements());
        return resources.nextElement();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      return loader.findResource(name);
    }
  }
}