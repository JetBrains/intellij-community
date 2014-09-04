/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {
  @NonNls String id() default "default";
  boolean isDefault() default true;
  @NonNls String file() default "";
  StorageScheme scheme() default StorageScheme.DEFAULT;

  /**
   * You must not store components with different roaming types in one file ({@link #file()}).
   */
  RoamingType roamingType() default RoamingType.PER_USER;

  Class<? extends StateStorage> storageClass() default StateStorage.class;
  Class<? extends StateSplitter> stateSplitter() default StateSplitter.class;
}
