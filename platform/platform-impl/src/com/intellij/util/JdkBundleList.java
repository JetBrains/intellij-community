// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Bitness;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class JdkBundleList {
  private static final Logger LOG = Logger.getInstance(JdkBundleList.class);

  private final Map<String, JdkBundle> myBundles = new LinkedHashMap<>();

  public void addBundlesFromLocation(@NotNull String location, @Nullable JavaVersion min, @Nullable JavaVersion max) {
    File[] vms = new File(location).listFiles();
    if (vms == null || vms.length == 0) return;

    Bitness arch = SystemInfo.is64Bit ? Bitness.x64 : Bitness.x32;
    boolean jdkRequired = "true".equals(System.getProperty("idea.jre.check"));
    for (File vm : vms) {
      JdkBundle bundle = JdkBundle.createBundle(vm);
      if (bundle != null) {
        LOG.trace(bundle.getLocation() + ": " + bundle.getBundleVersion() + ' ' + bundle.getBitness() + " jdk=" + bundle.isJdk());
        if (bundle.getBitness() == arch &&
            (min == null || bundle.getBundleVersion().compareTo(min) >= 0) &&
            (max == null || bundle.getBundleVersion().compareTo(max) < 0) &&
            (!jdkRequired || bundle.isJdk()) &&
            bundle.isOperational()) {
          addBundle(bundle);
        }
      }
    }
  }

  public void addBundle(@NotNull JdkBundle bundle) {
    String path = bundle.getLocation().getAbsolutePath();
    JdkBundle existing = myBundles.get(path);
    if (existing == null || bundle.isBoot() && !existing.isBoot()) {
      myBundles.put(path, bundle);
    }
  }

  @NotNull
  public Collection<JdkBundle> getBundles() {
    return Collections.unmodifiableCollection(myBundles.values());
  }

  @Nullable
  public JdkBundle getBundle(@NotNull String path) {
    return myBundles.get(path);
  }
}