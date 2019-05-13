/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class AnnotationOrderRootType extends PersistentOrderRootType {
  /**
   * @return External annotations path
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(AnnotationOrderRootType.class);
  }

  public AnnotationOrderRootType() {
    super("ANNOTATIONS", "annotationsPath", "annotation-paths", null);
  }

  @Override
  public boolean skipWriteIfEmpty() {
    return true;
  }

  @NotNull
  public static VirtualFile[] getFiles(@NotNull OrderEntry entry) {
    List<VirtualFile> result = new ArrayList<>();
    RootPolicy<List<VirtualFile>> policy = new RootPolicy<List<VirtualFile>>() {
      @Override
      public List<VirtualFile> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      @Override
      public List<VirtualFile> visitJdkOrderEntry(@NotNull final JdkOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      @Override
      public List<VirtualFile> visitModuleSourceOrderEntry(@NotNull final ModuleSourceOrderEntry orderEntry,
                                                           final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsRoots());
        return value;
      }
    };
    entry.accept(policy, result);
    return VfsUtilCore.toVirtualFileArray(result);
  }

  @NotNull
  public static String[] getUrls(@NotNull OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<List<String>>() {
      @Override
      public List<String> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitJdkOrderEntry(@NotNull final JdkOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitModuleSourceOrderEntry(@NotNull final ModuleSourceOrderEntry orderEntry,
                                                      final List<String> value) {
        Collections.addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsUrls());
        return value;
      }
    };
    entry.accept(policy, result);
    return ArrayUtil.toStringArray(result);
  }
}
