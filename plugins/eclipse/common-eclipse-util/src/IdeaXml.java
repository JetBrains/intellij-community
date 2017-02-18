/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NonNls;

public interface IdeaXml {
  @NonNls String COMPONENT_TAG = "component";
  @NonNls String CONTENT_ENTRY_TAG = "contentEntry";
  @NonNls String TEST_FOLDER_TAG = "testFolder";
  @NonNls String PACKAGE_PREFIX_TAG = "packagePrefix";
  @NonNls String PACKAGE_PREFIX_VALUE_ATTR = "value";
  @NonNls String EXCLUDE_FOLDER_TAG = "excludeFolder";
  @NonNls String URL_ATTR = "url";
  @NonNls String ECLIPSE_LIBRARY = "ECLIPSE";
  @NonNls String JAVA_SDK_TYPE = "JavaSDK";
  @NonNls String EXCLUDE_OUTPUT_TAG = "exclude-output";
  @NonNls String OUTPUT_TEST_TAG = "output-test";
  String JUNIT = "junit";
}