/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class BasePrimitiveBinding implements Binding {
  protected final Accessor myAccessor;
  protected final String myName;

  protected final @Nullable Converter<Object> myConverter;
  @Nullable protected Binding myBinding;

  protected BasePrimitiveBinding(@NotNull Accessor accessor, @Nullable String suggestedName, @Nullable Class<? extends Converter> converterClass) {
    myAccessor = accessor;
    myName = StringUtil.isEmpty(suggestedName) ? myAccessor.getName() : suggestedName;
    if (converterClass == null || converterClass == Converter.class) {
      myConverter = null;
    }
    else {
      //noinspection unchecked
      myConverter = XmlSerializerImpl.newInstance(converterClass);
    }
  }

  @Override
  public void init() {
    if (myConverter == null) {
      myBinding = XmlSerializerImpl.getBinding(myAccessor);
    }
  }
}