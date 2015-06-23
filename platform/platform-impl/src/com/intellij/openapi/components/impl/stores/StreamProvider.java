/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

public abstract class StreamProvider {
  public static final StreamProvider[] EMPTY_ARRAY = new StreamProvider[0];

  public boolean isEnabled() {
    return true;
  }

  /**
   * fileSpec Only main fileSpec, not version
   */
  public boolean isApplicable(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return true;
  }

  /**
   * @param fileSpec
   * @param content bytes of content, size of array is not actual size of data, you must use {@code size}
   * @param size actual size of data
   */
  public abstract void saveContent(@NotNull String fileSpec, @NotNull byte[] content, int size, @NotNull RoamingType roamingType) throws IOException;

  @Nullable
  public abstract InputStream loadContent(@NotNull String fileSpec, @NotNull RoamingType roamingType) throws IOException;

  @NotNull
  public Collection<String> listSubFiles(@NotNull String fileSpec, @NotNull RoamingType roamingType) {
    return Collections.emptyList();
  }

  /**
   * You must close passed input stream.
   */
  public void processChildren(@NotNull String path, @NotNull RoamingType roamingType, @NotNull Condition<String> filter, @NotNull ChildrenProcessor processor) {
    for (String name : listSubFiles(path, roamingType)) {
      if (!filter.value(name)) {
        continue;
      }

      InputStream input;
      try {
        input = loadContent(path + '/' + name, roamingType);
      }
      catch (IOException e) {
        StorageUtil.LOG.error(e);
        continue;
      }

      if (input != null && !processor.process(name, input)) {
        break;
      }
    }
  }

  public abstract static class ChildrenProcessor {
    public abstract boolean process(@NotNull String name, @NotNull InputStream input);
  }

  /**
   * Delete file or directory
   */
  public abstract void delete(@NotNull String fileSpec, @NotNull RoamingType roamingType);
}