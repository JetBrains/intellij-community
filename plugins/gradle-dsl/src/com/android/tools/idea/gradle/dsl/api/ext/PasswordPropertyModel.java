/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.api.ext;

/**
 * Model to represent a PasswordType property.
 * This class provides an easy way of working with the common formats for password properties.
 *
 * Currently this class supports 3 formats, see {@link PasswordType} for details.
 *
 * These models are not resolved, in order to get the resolved model please call {@link #resolve()}
 */
public interface PasswordPropertyModel extends MultiTypePropertyModel<PasswordPropertyModel.PasswordType> {
  enum PasswordType {
    /**
     * "propertyName &lt;value&gt;"
     */
    PLAIN_TEXT,
    /**
     * "System.getenv(&lt;value&gt;)"
     */
    ENVIRONMENT_VARIABLE,
    /**
     * "System.console().readLine(&lt;value&gt;)"
     */
    CONSOLE_READ,
  }
}
