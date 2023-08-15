// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

public interface ListItemEditor<T> extends CollectionItemEditor<T> {
  default @NotNull @NlsContexts.ListItem String getName(@NotNull T item) {
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
