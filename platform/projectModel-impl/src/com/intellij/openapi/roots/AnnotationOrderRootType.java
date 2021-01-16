// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
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

  public static VirtualFile @NotNull [] getFiles(@NotNull OrderEntry entry) {
    List<VirtualFile> result = new ArrayList<>();
    RootPolicy<List<VirtualFile>> policy = new RootPolicy<>() {
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
        Collections
          .addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsRoots());
        return value;
      }
    };
    entry.accept(policy, result);
    return VfsUtilCore.toVirtualFileArray(result);
  }

  public static String @NotNull [] getUrls(@NotNull OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<>() {
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
    return ArrayUtilRt.toStringArray(result);
  }
}
