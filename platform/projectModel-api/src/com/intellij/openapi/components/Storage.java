// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components;

import com.intellij.openapi.application.Application;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines persistence storage location and options.
 * See <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Persisting States</a>.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Storage {
  /**
   * @deprecated Use {@link #value()}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String file() default "";

  /**
   * The configuration root path relative to component container.
   * Consider using shorthand form - {@code @Storage("yourName.xml")} (when you need to specify only file path).
   * <p>
   * Consider reusing existing storage files instead of a new one to avoid creating too many of them.
   * Related components should reuse the same storage file.
   * But don't mix components with different RoamingTypes in a single file, it is prohibited.
   * <p>
   * The actual path to the storage file on disk is not strictly defined as relative to the container path,
   * in fact it can be different, e.g. application-wide {@link RoamingType#PER_OS os-dependent} settings are stored in the subfolder
   * correspondent to the current OS, e.g. in {@code APP_CONFIG/options/mac/}.
   *
   * @see StoragePathMacros
   */
  @NonNls
  String value() default "";

  /**
   * If deprecated, data will be removed on write. And ignored on read if (and only if) new storage exists.
   */
  boolean deprecated() default false;

  /**
   * Used by the Settings Repository plugin to determine how application-level settings should be shared between different IDE installations.
   * You must not store components with different roaming types in one file ({@link #value()}).
   */
  RoamingType roamingType() default RoamingType.DEFAULT;

  /**
   * Class must have constructor {@code (String fileSpec, ComponentManager componentManager, StateStorageManager storageManager)}.
   * {@code componentManager} parameter can have more concrete type - e.g. {@code Module} (if storage intended to support only one type).
   */
  Class<? extends StateStorage> storageClass() default StateStorage.class;

  @SuppressWarnings("deprecation")
  Class<? extends StateSplitter> stateSplitter() default StateSplitterEx.class;

  /**
   * Whether to apply save threshold policy (defaults to {@code true} if {@link #roamingType()} is set to {@link RoamingType#DISABLED}).
   * <p>
   * If the threshold is enabled, calls of {@link Application#saveSettings()} will save the component at most once in 5 minutes, but if user
   * explicitly invokes 'Save All' action, the component will be saved immediately. Use this attribute to disable the threshold for components
   * which configuration may be read from external processes, and therefore it's important to save them immediately.
   * </p>
   */
  ThreeState useSaveThreshold() default ThreeState.UNSURE;

  @ApiStatus.Internal
  boolean exclusive() default false;

  /**
   * Is exportable (Export Settings dialog) regardless of roaming type.
   */
  boolean exportable() default false;

  @ApiStatus.Internal
  boolean usePathMacroManager() default true;
}