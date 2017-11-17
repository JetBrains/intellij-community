// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.util.Getter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Persisting States</a>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface State {
  /**
   * Component name.
   */
  String name();

  /**
   * <p>Storages specification.</p>
   *
   * <p>Application-level: optional if you need to load only default state ({@link #defaultStateAsResource} must be true in this case).</p>
   *
   * <p>Project-level: optional, standard project file will be used by default
   * ({@code *.ipr} file for file-based and
   * {@code .idea/misc.xml} for directory-based).</p>
   *
   * <p>Module-level: optional, corresponding module file will be used ({@code *.iml}).</p>
   */
  Storage[] storages() default {};

  /**
   * If set to false, complete project (or application) reload is required when the storage file is changed externally and the state has changed.
   */
  boolean reloadable() default true;

  /**
   * If true, default state will be loaded from resources (if exists).
   */
  boolean defaultStateAsResource() default false;

  /**
   * Additional export path (relative to application-level configuration root directory).
   */
  String additionalExportFile() default "";

  Class<? extends NameGetter> presentableName() default NameGetter.class;

  /**
   * Is this component intended to store data only in the external storage.
   */
  boolean externalStorageOnly() default false;

  abstract class NameGetter implements Getter<String> {
  }
}
