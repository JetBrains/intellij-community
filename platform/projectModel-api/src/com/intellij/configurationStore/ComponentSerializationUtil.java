// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author nik
 */
public class ComponentSerializationUtil {
  @NotNull
  public static <S> Class<S> getStateClass(@NotNull Class<? extends PersistentStateComponent> aClass) {
    TypeVariable<Class<PersistentStateComponent>> variable = PersistentStateComponent.class.getTypeParameters()[0];
    Type type = ReflectionUtil.resolveVariableInHierarchy(variable, aClass);
    assert type != null : aClass;
    @SuppressWarnings("unchecked") Class<S> result = (Class<S>)ReflectionUtil.getRawType(type);
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
