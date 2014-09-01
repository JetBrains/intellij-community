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

package com.intellij.openapi.components;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface State {
  String name();

  /**
   * {@link RoamingType#GLOBAL} will be ignored because it doesn't matter - global or per project depends on file spec (used storage macros).
   *
   * You must not store components with different roaming types in one file ({@link com.intellij.openapi.components.Storage#file()}).
   */
  RoamingType roamingType() default RoamingType.PER_USER;

  Storage[] storages();

  Class<? extends StateStorageChooser> storageChooser() default StorageAnnotationsDefaultValues.NullStateStorageChooser.class;

  boolean reloadable() default true;
}