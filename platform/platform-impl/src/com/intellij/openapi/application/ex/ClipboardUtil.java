/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.util.function.Supplier;

public class ClipboardUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.Clipboard");

  public static <E> E handleClipboardSafely(@NotNull Supplier<E> supplier, @NotNull Supplier<E> onFail) {
    try {
      return useLegacyMergeSort(supplier);
    }
    catch (IllegalStateException e) {
      if (SystemInfo.isWindows) {
        LOG.debug("Clipboard is busy");
      }
      else {
        LOG.warn(e);
      }
    }
    catch (NullPointerException e) {
      LOG.warn("Java bug #6322854", e);
    }
    catch (IllegalArgumentException e) {
      LOG.warn("Java bug #7173464", e);
    }
    return onFail.get();
  }

  private static final String USE_LEGACY_MERGE_SORT_PROPERTY_NAME = "java.util.Arrays.useLegacyMergeSort";

  private static <T> T useLegacyMergeSort(Supplier<T> supplier) {
    String originalValue = System.getProperty(USE_LEGACY_MERGE_SORT_PROPERTY_NAME);
    System.setProperty(USE_LEGACY_MERGE_SORT_PROPERTY_NAME, "true");
    try {
      return supplier.get();
    }
    finally {
      if (originalValue != null) {
        System.setProperty(USE_LEGACY_MERGE_SORT_PROPERTY_NAME, originalValue);
      }
    }
  }

  @Nullable
  public static String getTextInClipboard() {
    return CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
  }
}