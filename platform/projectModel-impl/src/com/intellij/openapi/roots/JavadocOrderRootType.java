// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


public class JavadocOrderRootType extends PersistentOrderRootType {
  @ApiStatus.Internal
  public JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", "javadoc-paths", "javadocPathEntry");
  }

  @NotNull
  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }

  private final static RootPolicy<String @NotNull []> GET_JAVADOC_URL_POLICY = new RootPolicy<>() {
    @Override
    public String @NotNull [] visitLibraryOrderEntry(@NotNull LibraryOrderEntry libraryOrderEntry, String[] value) {
      return libraryOrderEntry.getRootUrls(getInstance());
    }

    @Override
    public String @NotNull [] visitJdkOrderEntry(@NotNull JdkOrderEntry jdkOrderEntry, String[] value) {
      return jdkOrderEntry.getRootUrls(getInstance());
    }

    @Override
    public String @NotNull [] visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry moduleSourceOrderEntry, String[] value) {
      return moduleSourceOrderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getJavadocUrls();
    }
  };
  public static String @NotNull [] getUrls(@NotNull OrderEntry entry) {
    return entry.accept(GET_JAVADOC_URL_POLICY, null);
  }
}
