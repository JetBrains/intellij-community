// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.util.collectionUtil;

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;

import java.util.ArrayList;
import java.util.List;

public final class GradleCollectionUtil {

  public static <T> void configureEach(DomainObjectCollection<? extends T> collection, Action<? super T> action) {
    if (GradleVersionUtil.isCurrentGradleAtLeast("4.9")) {
      collection.configureEach(action);
      return;
    }
    collection.all(action);
  }

  public static List<String> collectionToString(Iterable<?> collection) {
    List<String> result = new ArrayList<>();
    for (Object item : collection) {
      result.add(String.valueOf(item));
    }
    return result;
  }
}
