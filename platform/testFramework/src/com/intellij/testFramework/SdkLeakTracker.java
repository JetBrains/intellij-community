// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.PlatformUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.junit.Assert;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

@TestOnly
public class SdkLeakTracker {
  @NotNull
  private final Sdk[] oldSdks;
  public SdkLeakTracker() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    oldSdks = table == null ? new Sdk[0] : table.getAllJdks();
  }

  public void checkForJdkTableLeaks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();
    if (table != null) {
      Sdk[] jdks = table.getAllJdks();
      if (jdks.length != 0) {
        Set<Sdk> leaked = new THashSet<>(Arrays.asList(jdks));
        Set<Sdk> old = new THashSet<>(Arrays.asList(oldSdks));
        leaked.removeAll(old);

        // Android Studio: AndroidStudioGradleInstallationManager#getGradleJdk has the side effect of adding the (embedded) JDK to the
        // application-level ProjectJdkTable. It is called through the GradleInstallationManager service, and the Kotlin plugin calls it
        // early on during initialization (while preparing Gradle paths, JVM args, etc). This is not a real leak, because a Gradle sync
        // will also attempt to add this JDK to the table, and there is caching to ensure deduplication.
        // Note that AndroidTestCase gave up on leak checking by clearing the ProjectJdkTable during teardown, so this code is only
        // reachable for tests extending IdeaTestCase or PlatformTestCase directly.
        if (PlatformUtils.isAndroidStudio()) {
          exemptKotlinJdk(leaked);
          if (!leaked.isEmpty()) {
            exemptGradleJdk(leaked);
          }
        }

        try {
          if (!leaked.isEmpty()) {
            Assert.fail("Leaked SDKs: " + leaked);
          }
        }
        finally {
          for (Sdk jdk : leaked) {
            WriteAction.run(()-> table.removeJdk(jdk));
          }
        }
      }
    }
  }

  private static void exemptGradleJdk(Set<Sdk> leaked) {
    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if ("org.jetbrains.plugins.gradle".equals(descriptor.getPluginId().getIdString())) {
        try {
          Class<?> serviceClass =
            descriptor.getPluginClassLoader().loadClass("org.jetbrains.plugins.gradle.service.GradleInstallationManager");
          Object serviceInstance = ServiceManager.getService(serviceClass);
          Method getGradleJdk = serviceClass.getMethod("getGradleJdk", Project.class, String.class);
          Object jdk = getGradleJdk.invoke(serviceInstance, null, "ignored");

          if (jdk instanceof Sdk) {
            leaked.remove(jdk);
            WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk((Sdk) jdk));
          }
        } catch (ReflectiveOperationException ignored) {
        }
      }
    }
  }

  private static void exemptKotlinJdk(Set<Sdk> leaked) {
    Sdk kotlinSdk = null;
    for (Sdk sdk : leaked) {
      if ("Kotlin SDK".equals(sdk.getName())) {
        kotlinSdk = sdk;
        break;
      }
    }
    if (kotlinSdk != null) {
      final Sdk sdk = kotlinSdk;
      leaked.remove(sdk);
      WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk(sdk));
    }
  }
}
