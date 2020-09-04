/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Represents the property type for a {@link GradleDslElement}.
 * <ul>
 * <li>{@code REGULAR} - a Gradle property, e.g "ext.prop1 = 'value'"</li>
 * <li>{@code VARIABLE} - a DSL variable, e.g "def prop1 = 'value'"</li>
 * <li>{@code DERIVED} - an internal property derived from values in a map or list, e.g. property "key"
 * in "prop1 = ["key" : 'value']"</li>
 * <li>{@code GLOBAL}   - this is a global property defined by Gradle e.g projectDir</li>
 * <li>{@code PROPERTIES_FILE} - a Gradle property from a gradle.properties file</li>
 * <li>{@code FAKE} - a fake property is used to represent some "part" of another property to make it easier to work with,
 * some operations (such as making them lists of maps) are disabled for these properties. An example of a FAKE
 * property is a component of a Gradle coordinate in compact notation e.g ('com.android.support:appcompat-v7:22.1.1')</li>
 * </ul>
 */
public enum PropertyType {
  REGULAR,
  VARIABLE,
  DERIVED,
  GLOBAL,
  PROPERTIES_FILE,
  FAKE,
}
