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

import com.intellij.openapi.util.Getter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface State {
  String name();

  @Deprecated
  /**
   * @deprecated Use {@link Storage#roamingType()}
   */
  RoamingType roamingType() default RoamingType.PER_USER;

  Storage[] storages();

  Class<? extends StateStorageChooser> storageChooser() default StateStorageChooser.class;

  boolean reloadable() default true;

  /**
   * If true, default state will be loaded from resources (if exists)
   */
  boolean defaultStateAsResource() default false;

  String additionalExportFile() default "";

  Class<? extends NameGetter> presentableName() default NameGetter.class;

  abstract class NameGetter implements Getter<String> {
  }
}
