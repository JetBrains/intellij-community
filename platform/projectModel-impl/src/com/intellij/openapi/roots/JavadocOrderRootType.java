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

  public static OrderRootType getInstance() {
    return getOrderRootType(JavadocOrderRootType.class);
  }

  public static String[] getUrls(OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<List<String>>() {
      @Override
      public List<String> visitLibraryOrderEntry(final LibraryOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitJdkOrderEntry(final JdkOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitModuleSourceOrderEntry(final ModuleSourceOrderEntry orderEntry,
                                                           final List<String> value) {
        Collections.addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getJavadocUrls());
        return value;
      }
    };
    entry.accept(policy, result);
    return ArrayUtil.toStringArray(result);
  }
}
