/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

abstract class BasePrimitiveBinding extends Binding {
  protected final String myName;

  @Nullable
  protected final Converter<Object> myConverter;

  @Nullable
  protected Binding myBinding;

  protected BasePrimitiveBinding(@NotNull MutableAccessor accessor, @Nullable String suggestedName, @Nullable Class<? extends Converter> converterClass) {
    super(accessor);

    myName = StringUtil.isEmpty(suggestedName) ? myAccessor.getName() : suggestedName;
    //noinspection unchecked
    myConverter = converterClass == null || converterClass == Converter.class ? null : ReflectionUtil.newInstance(converterClass);
  }

  @Override
  public final void init(@NotNull Type originalType, @NotNull Serializer serializer) {
    if (myConverter == null && !(this instanceof AttributeBinding)) {
      myBinding = serializer.getBinding(myAccessor);
    }
  }

  @Nullable
  @Override
  public final Object serialize(@NotNull Object o, @Nullable Object context, @Nullable SerializationFilter filter) {
    return serialize(o, filter);
  }

  @Nullable
  public abstract Object serialize(@NotNull Object o, @Nullable SerializationFilter filter);

  @NotNull
  public Object deserialize(@NotNull Object context, @NotNull Element element) {
    return context;
  }

  @Override
  public final Object deserializeUnsafe(Object context, @NotNull Element element) {
    return deserialize(context, element);
  }

  @Nullable
  protected final Converter<Object> getConverter() {
    //Converter<Object> converter = myConverter;
    //if (converter == null && o instanceof Converter) {
    //  try {
    //    //noinspection unchecked
    //    converter = (Converter<Object>)o;
    //  }
    //  catch (Exception e) {
    //    LOG.warn(e);
    //  }
    //}
    return myConverter;
  }
}