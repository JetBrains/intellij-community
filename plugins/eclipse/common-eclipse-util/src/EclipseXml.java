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

public interface EclipseXml {
  @NonNls String UNNAMED_PROJECT = "unnamed";
  @NonNls String PROJECT_EXT = "project";
  @NonNls String PROJECT_FILE = "." + PROJECT_EXT;
  @NonNls String NAME_TAG = "name";
  @NonNls String CLASSPATH_EXT = "classpath";
  @NonNls String CLASSPATH_FILE = "." + CLASSPATH_EXT;
  @NonNls String CLASSPATH_TAG = "classpath";
  @NonNls String CLASSPATHENTRY_TAG = "classpathentry";
  @NonNls String KIND_ATTR = "kind";
  @NonNls String PATH_ATTR = "path";
  @NonNls String EXPORTED_ATTR = "exported";
  @NonNls String TRUE_VALUE = "true";
  @NonNls String SRC_KIND = "src";
  @NonNls String COMBINEACCESSRULES_ATTR = "combineaccessrules";
  @NonNls String FALSE_VALUE = "false";
  @NonNls String LIB_KIND = "lib";
  @NonNls String SOURCEPATH_ATTR = "sourcepath";
  @NonNls String VAR_KIND = "var";
  @NonNls String CON_KIND = "con";
  @NonNls String JRE_CONTAINER = "org.eclipse.jdt.launching.JRE_CONTAINER";
  @NonNls String JRE_CONTAINER_SPECIFIC = JRE_CONTAINER + "/" + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType";
  @NonNls String USER_LIBRARY = "org.eclipse.jdt.USER_LIBRARY";
  @NonNls String JUNIT_CONTAINER = "org.eclipse.jdt.junit.JUNIT_CONTAINER";
  @NonNls String GROOVY_DSL_CONTAINER = "GROOVY_DSL_SUPPORT";
  @NonNls String GROOVY_SUPPORT = "GROOVY_SUPPORT";
  @NonNls String JREBEL_NATURE = "org.zeroturnaround.eclipse.jrebelNature";
  @NonNls String JAVA_NATURE = "org.eclipse.jdt.core.javanature";
  @NonNls String SONAR_NATURE = "org.sonar.ide.eclipse.core.sonarNature";
  @NonNls String JUNIT3 = JUNIT_CONTAINER + "/" + "3.8.1";
  @NonNls String JUNIT4 = JUNIT_CONTAINER + "/" + "4";
  @NonNls String ECLIPSE_PLATFORM = "org.eclipse.pde.core.requiredPlugins";
  @NonNls String OUTPUT_KIND = "output";
  @NonNls String PLUGIN_XML_FILE = "plugin.xml";
  @NonNls String ID_ATTR = "id";
  @NonNls String REQUIRES_TAG = "requires";
  @NonNls String IMPORT_TAG = "import";
  @NonNls String PLUGIN_ATTR = "plugin";
  @NonNls String EXPORT_ATTR = "export";
  @NonNls String ORG_JUNIT_PLUGIN = "org.junit";
  @NonNls String PROJECT_CONTEXT = "project";
  @NonNls String TEMPLATE_CONTEXT = "template";
  @NonNls String BIN_DIR = "bin";
  @NonNls String IDEA_SETTINGS_POSTFIX = ".eml";
  @NonNls String ECLIPSE_JAR_PREFIX = "jar:file:/";
  @NonNls String ECLIPSE_FILE_PREFIX = "file:/";
  @NonNls String ATTRIBUTES_TAG = "attributes";
  @NonNls String ATTRIBUTE_TAG = "attribute";
  @NonNls String NAME_ATTR = "name";
  @NonNls String VALUE_ATTR = "value";
  @NonNls String DOT_CLASSPATH_EXT = "." + CLASSPATH_EXT;
  @NonNls String DOT_PROJECT_EXT = "." + PROJECT_EXT;
  @NonNls String FILE_PROTOCOL = "file:/";
  @NonNls String PLATFORM_PROTOCOL = "platform:/";
  @NonNls String JAR_PREFIX = "jar:";
  String JAVA_SDK_TYPE = "/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType";
  @NonNls String LINKED_RESOURCES = "linkedResources";
  @NonNls String LINK = "link";
  @NonNls String JAVADOC_LOCATION = "javadoc_location";
  @NonNls String DLL_LINK = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";
}
