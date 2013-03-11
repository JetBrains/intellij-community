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
package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NonNls;

public interface IdeaXml {
  @NonNls String COMPONENT_TAG = "component";
  @NonNls String NAME_ATTR = "name";
  @NonNls String NEW_MODULE_ROOT_MANAGER_VALUE = "NewModuleRootManager";
  @NonNls String ORDER_ENTRY_TAG = "orderEntry";
  @NonNls String TYPE_ATTR = "type";
  @NonNls String EXPORTED_ATTR = "exported";
  @NonNls String SOURCE_FOLDER_TYPE = "sourceFolder";
  @NonNls String SOURCE_FOLDER_TAG = "sourceFolder";
  @NonNls String CONTENT_ENTRY_TAG = "contentEntry";
  @NonNls String TEST_FOLDER_TAG = "testFolder";
  @NonNls String PACKAGE_PREFIX_TAG = "packagePrefix";
  @NonNls String PACKAGE_PREFIX_VALUE_ATTR = "value";
  @NonNls String EXCLUDE_FOLDER_TAG = "excludeFolder";
  @NonNls String FOR_TESTS_ATTR = "forTests";
  @NonNls String TRUE_VALUE = "true";
  @NonNls String FALSE_VALUE = "false";
  @NonNls String CONTENT_TAG = "content";
  @NonNls String MODULE_LIBRARY_TYPE = "module-library";
  @NonNls String LIBRARY_TAG = "library";
  @NonNls String ROOT_TAG = "root";
  @NonNls String CLASSES_TAG = "CLASSES";
  @NonNls String SOURCES_TAG = "SOURCES";
  @NonNls String JAVADOC_TAG = "JAVADOC";
  @NonNls String JAR_DIR = "jarDirectory";
  @NonNls String URL_ATTR = "url";
  @NonNls String LIBRARY_TYPE = "library";
  @NonNls String LEVEL_ATTR = "level";
  @NonNls String APPLICATION_LEVEL = "application";
  @NonNls String PROJECT_LEVEL = "project";
  @NonNls String ECLIPSE_LIBRARY = "ECLIPSE";
  @NonNls String MODULE_TYPE = "module";
  @NonNls String MODULE_NAME_ATTR = "module-name";
  @NonNls String JDK_TYPE = "jdk";
  @NonNls String INHERITED_JDK_TYPE = "inheritedJdk";
  @NonNls String JDK_NAME_ATTR = "jdkName";
  @NonNls String JDK_TYPE_ATTR = "jdkType";
  @NonNls String JAVA_SDK_TYPE = "JavaSDK";
  @NonNls String MODULE_DIR_MACRO = "$MODULE_DIR$";
  @NonNls String FILE_PREFIX = "file://";
  @NonNls String JAR_PREFIX = "jar://";
  @NonNls String JAR_SUFFIX = "!/";
  @NonNls String JAR_EXT = ".jar";
  @NonNls String ZIP_EXT = ".zip";
  @NonNls String EXCLUDE_OUTPUT_TAG = "exclude-output";
  @NonNls String LANGUAGE_LEVEL_ATTR = "LANGUAGE_LEVEL";
  @NonNls String INHERIT_COMPILER_OUTPUT_ATTR = "inherit-compiler-output";
  @NonNls String OUTPUT_TAG = "output";
  @NonNls String OUTPUT_TEST_TAG = "output-test";
  @NonNls String IS_TEST_SOURCE_ATTR = "isTestSource";
  @NonNls String ORDER_ENTRY_PROPERTIES_TAG = "orderEntryProperties";
  @NonNls String IPR_EXT = ".ipr";
  @NonNls String PROJECT_MODULE_MANAGER_VALUE = "ProjectModuleManager";
  @NonNls String MODULES_TAG = "modules";
  @NonNls String MODULE_TAG = "module";
  @NonNls String FILEURL_ATTR = "fileurl";
  @NonNls String FILEPATH_ATTR = "filepath";
  @NonNls String PROJECT_DIR_MACRO = "$PROJECT_DIR$";
  @NonNls String PROJECT_PREFIX = FILE_PREFIX + PROJECT_DIR_MACRO + "/";
  @NonNls String USED_PATH_MACROS_TAG = "UsedPathMacros";
  @NonNls String MACRO_TAG = "macro";
  @NonNls String UNNAMED_PROJECT = "unnamed";
  @NonNls String MODULE_CONTEXT = "module";
  @NonNls String PROJECT_CONTEXT = "project";
  @NonNls String CLASSPATH_CONTEXT = "classpath";
  @NonNls String TEMPLATE_CONTEXT = "template";
  @NonNls String EXCLUDE_OUTPUT = "exclude-output";
  @NonNls String IML_EXT = ".iml";
  String JUNIT = "junit";
}
