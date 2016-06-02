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
package com.intellij.util;

import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.EventListener;

import static com.intellij.util.ui.UIUtil.uiTraverser;

/**
 * @author gregsh
 */
public class ComponentTreeEventDispatcher<T extends EventListener> {

  private final Class<T> myListenerClass;
  private final T myMulticaster;

  public static <T extends EventListener> ComponentTreeEventDispatcher<T> create(@NotNull Class<T> listenerClass) {
    return new ComponentTreeEventDispatcher<T>(listenerClass);
  }

  private ComponentTreeEventDispatcher(@NotNull Class<T> listenerClass) {
    myListenerClass = listenerClass;
    myMulticaster = EventDispatcher.createMulticaster(listenerClass, new Getter<Iterable<T>>() {
      @Override
      public Iterable<T> get() {
        return uiTraverser(null)
          .withRoots(Arrays.asList(Window.getWindows()))
          .postOrderDfsTraversal()
          .filter(myListenerClass);
      }
    });
  }

  @NotNull
  public T getMulticaster() {
    return myMulticaster;
  }

}
