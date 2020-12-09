/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

public interface ListItemEditor<T> extends CollectionItemEditor<T> {
  @NotNull
  default @NlsContexts.ListItem String getName(@NotNull T item) {
    //noinspection HardCodedStringLiteral
    return item.toString();
  }

  default void applyModifiedProperties(@NotNull T newItem, @NotNull T oldItem) {
    XmlSerializerUtil.copyBean(newItem, oldItem);
  }

  default boolean isEditable(@NotNull T item) {
    return true;
  }
}
