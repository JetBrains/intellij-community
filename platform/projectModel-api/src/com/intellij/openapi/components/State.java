// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Supplier;

/**
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/persisting_state_of_components.html">Persisting States</a>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface State {
  /**
   * Component name.
   */
  @NotNull @NonNls
  String name();

  /**
   * <p>Storages specification.</p>
   *
   * <p>Project-level: optional, standard project file will be used by default
   * ({@code *.ipr} file for file-based and
   * {@code .idea/misc.xml} for directory-based).</p>
   *
   * <p>Module-level: optional, corresponding module file will be used ({@code *.iml}).</p>
   */
  Storage @NotNull [] storages() default {};

  /**
   * If set to false, complete project (or application) reload is required when the storage file is changed externally and the state has changed.
   */
  boolean reloadable() default true;

  /**
   * If true, default state will be loaded from resources (if exists).
   */
  boolean defaultStateAsResource() default false;

  /**
   * Additional export directory path (relative to application-level configuration root directory).
   */
  @NotNull String additionalExportDirectory() default "";

  /**
   * @deprecated Use {@link #additionalExportDirectory()}.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  String additionalExportFile() default "";

  Class<? extends NameGetter> presentableName() default NameGetter.class;

  /**
   * Is this component intended to store data only in the external storage.
   */
  boolean externalStorageOnly() default false;

  /**
   * <p>Enables recording of boolean and numerical fields, if true and statistics is allowed.</p>
   * <br/>
   * <p>Boolean: records not default value of the field.</p>
   * <p>Numerical/Enums/Strings: records an event that the value is not default.
   * To record an absolute value of the field, add {@link ReportValue} annotation. </p>
   *
   * <br/>
   * <i>Limitations:</i><ul>
   * <li>Won't record the value of object</li>
   * <li>Won't record fields if state is persisted manually, i.e. the state is {@link org.jdom.Element} </li>
   * </ul>
   */
  boolean reportStatistic() default true;

  boolean allowLoadInTests() default false;

  @ApiStatus.Internal
  boolean useLoadedStateAsExisting() default true;

  abstract class NameGetter implements Supplier<@Nls String> {
    @Override
    public abstract @Nls String get();
  }

  SettingsCategory category() default SettingsCategory.OTHER;
}
