/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.components;

import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Persisting States</a>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {
  /**
   * @deprecated Use {@link #value()}.
   */
  @Deprecated
  String file() default "";

  /**
   * Relative to component container configuration root path.
   * Consider to use shorthand form - {@code @Storage("yourName.xml")} (when you need to specify only file path).
   */
  @NonNls
  String value() default "";

  /**
   * If deprecated: Data will be removed on write. And ignored on read if (and only if) new storage exists.
   */
  boolean deprecated() default false;

  /**
   * Used by the Settings Repository plugin to determine how application-level settings should be shared between different IDE installations.
   * You must not store components with different roaming types in one file ({@link #value()}).
   */
  RoamingType roamingType() default RoamingType.DEFAULT;

  /**
   * Class must have constructor {@code (String fileSpec, ComponentManager componentManager, StateStorageManager storageManager)}.
   * {@code componentManager} parameter can have more concrete type - e.g. Module (if storage intended to support only one type).
   */
  Class<? extends StateStorage> storageClass() default StateStorage.class;

  @SuppressWarnings("deprecation")
  Class<? extends StateSplitter> stateSplitter() default StateSplitterEx.class;

  /**
   * @deprecated Not required and not used anymore.
   */
  @Deprecated
  StorageScheme scheme() default StorageScheme.DEFAULT;

  /**
   * @deprecated Not required and not used anymore.
   */
  @Deprecated
  String id() default "default";

  // internal use only
  boolean exclusive() default false;
}