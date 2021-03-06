// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class JavadocOrderRootType extends PersistentOrderRootType {
  public JavadocOrderRootType() {
    super("JAVADOC", "javadocPath", "javadoc-paths", "javadocPathEntry");
  }

  @NotNull
  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }

  public static String @NotNull [] getUrls(@NotNull OrderEntry entry) {
    return ((JavadocOrderRootType)getInstance()).doGetUrls(entry);
  }

  private String @NotNull [] doGetUrls(@NotNull OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<>() {
      @Override
      public List<String> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(JavadocOrderRootType.this));
        return value;
      }

      @Override
      public List<String> visitJdkOrderEntry(@NotNull final JdkOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(JavadocOrderRootType.this));
        return value;
      }

      @Override
      public List<String> visitModuleSourceOrderEntry(@NotNull final ModuleSourceOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getJavadocUrls());
        return value;
      }
    };
    entry.accept(policy, result);
    return ArrayUtilRt.toStringArray(result);
  }
}
