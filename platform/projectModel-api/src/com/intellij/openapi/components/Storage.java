// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Persisting States</a>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {
  String NOT_ROAMABLE_FILE = "other.xml";

  /**
   * @deprecated Use {@link #value()}.
   */
  @Deprecated
  String file() default "";

  /**
   * Relative to component container configuration root path.
   * Consider using shorthand form - {@code @Storage("yourName.xml")} (when you need to specify only file path).
   *
   * Consider reusing existing storage files instead of a new one. No one need myriads config files. Related components should reuse storage file.
   *
   * @see StoragePathMacros
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
   * Whether to apply save threshold policy (defaults to true if roamingType is set to DISABLED)
   */
  ThreeState useSaveThreshold() default ThreeState.UNSURE;

  // internal use only
  boolean exclusive() default false;

  /**
   * Is exportable (Export Settings dialog) regardless of roaming type.
   */
  boolean exportable() default false;
}