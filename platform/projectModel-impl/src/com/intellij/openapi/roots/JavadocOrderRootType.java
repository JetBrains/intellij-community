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

import com.intellij.util.ArrayUtil;
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

  @NotNull
  public static String[] getUrls(@NotNull OrderEntry entry) {
    return ((JavadocOrderRootType)getInstance()).doGetUrls(entry);
  }

  @NotNull
  private String[] doGetUrls(@NotNull OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<List<String>>() {
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
    return ArrayUtil.toStringArray(result);
  }
}
