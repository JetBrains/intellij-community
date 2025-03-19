// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


public class AnnotationOrderRootType extends PersistentOrderRootType {
  public static final String ANNOTATIONS_ID = "ANNOTATIONS";

  /**
   * @return External annotations path
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(AnnotationOrderRootType.class);
  }

  @ApiStatus.Internal
  public AnnotationOrderRootType() {
    super(ANNOTATIONS_ID, "annotationsPath", "annotation-paths", null);
  }

  @Override
  public boolean skipWriteIfEmpty() {
    return true;
  }

  public static VirtualFile @NotNull [] getFiles(@NotNull OrderEntry entry) {
    if (entry instanceof LibraryOrderEntry orderEntry) {
      return orderEntry.getRootFiles(getInstance());
    }
    else if (entry instanceof JdkOrderEntry orderEntry) {
      return orderEntry.getRootFiles(getInstance());
    }
    else if (entry instanceof ModuleSourceOrderEntry orderEntry) {
      JavaModuleExternalPaths moduleExtension = orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class);
      if (moduleExtension != null) {
        return moduleExtension.getExternalAnnotationsRoots();
      }
      else {
        return VirtualFile.EMPTY_ARRAY;
      }
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  private static final RootPolicy<String[]> GET_ANNOTATION_URL_POLICY = new RootPolicy<>() {
    @Override
    public String[] visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry, String[] value) {
      return libraryOrderEntry.getRootUrls(getInstance());
    }

    @Override
    public String[] visitJdkOrderEntry(@NotNull JdkOrderEntry jdkOrderEntry, String[] value) {
      return jdkOrderEntry.getRootUrls(getInstance());
    }

    @Override
    public String[] visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry moduleSourceOrderEntry, String[] value) {
      return moduleSourceOrderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsUrls();
    }
  };

  @ApiStatus.Internal
  public static boolean hasUrls(@NotNull OrderEntry entry) {
    String[] urls = entry.accept(GET_ANNOTATION_URL_POLICY, null);
    return urls != null && urls.length != 0;
  }
}
