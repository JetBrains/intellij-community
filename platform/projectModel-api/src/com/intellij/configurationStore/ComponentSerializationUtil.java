// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public final class ComponentSerializationUtil {
  public static @NotNull <S> Class<S> getStateClass(@NotNull Class<? extends PersistentStateComponent> aClass) {
    TypeVariable<Class<PersistentStateComponent>> variable = PersistentStateComponent.class.getTypeParameters()[0];
    Type type = ClassUtil.resolveVariableInHierarchy(variable, aClass);
    assert type != null : aClass;
    @SuppressWarnings("unchecked") Class<S> result = (Class<S>)ClassUtil.getRawType(type);
    if (result == Object.class) {
      //noinspection unchecked
      return (Class<S>)aClass;
    }
    return result;
  }

  public static <S> void loadComponentState(@NotNull PersistentStateComponent<S> configuration, @Nullable Element element) {
    if (element != null) {
      Class<S> stateClass = getStateClass(configuration.getClass());
      @SuppressWarnings("unchecked") S state = stateClass.equals(Element.class) ? (S)element : XmlSerializer.deserialize(element, stateClass);
      configuration.loadState(state);
    }
  }
}
