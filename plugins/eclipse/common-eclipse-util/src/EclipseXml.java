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

@NonNls
public interface EclipseXml {
   String UNNAMED_PROJECT = "unnamed";
   String PROJECT_EXT = "project";
   String PROJECT_FILE = "." + PROJECT_EXT;
   String NAME_TAG = "name";
   String CLASSPATH_EXT = "classpath";
   String CLASSPATH_FILE = "." + CLASSPATH_EXT;
   String CLASSPATH_TAG = "classpath";
   String CLASSPATHENTRY_TAG = "classpathentry";
   String KIND_ATTR = "kind";
   String PATH_ATTR = "path";
   String EXPORTED_ATTR = "exported";
   String TRUE_VALUE = "true";
   String SRC_KIND = "src";
   String COMBINEACCESSRULES_ATTR = "combineaccessrules";
   String FALSE_VALUE = "false";
   String LIB_KIND = "lib";
   String SOURCEPATH_ATTR = "sourcepath";
   String VAR_KIND = "var";
   String CON_KIND = "con";
   String JRE_CONTAINER = "org.eclipse.jdt.launching.JRE_CONTAINER";
   String JRE_CONTAINER_SPECIFIC = JRE_CONTAINER + "/" + "org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType";
   String USER_LIBRARY = "org.eclipse.jdt.USER_LIBRARY";
   String JUNIT_CONTAINER = "org.eclipse.jdt.junit.JUNIT_CONTAINER";
   String GROOVY_DSL_CONTAINER = "GROOVY_DSL_SUPPORT";
   String GROOVY_SUPPORT = "GROOVY_SUPPORT";
   String JREBEL_NATURE = "org.zeroturnaround.eclipse.jrebelNature";
   String JAVA_NATURE = "org.eclipse.jdt.core.javanature";
   String SONAR_NATURE = "org.sonar.ide.eclipse.core.sonarNature";
   String JUNIT3 = JUNIT_CONTAINER + "/" + "3.8.1";
   String JUNIT4 = JUNIT_CONTAINER + "/" + "4";
   String ECLIPSE_PLATFORM = "org.eclipse.pde.core.requiredPlugins";
   String OUTPUT_KIND = "output";
   String PLUGIN_XML_FILE = "plugin.xml";
   String ID_ATTR = "id";
   String REQUIRES_TAG = "requires";
   String IMPORT_TAG = "import";
   String PLUGIN_ATTR = "plugin";
   String EXPORT_ATTR = "export";
   String ORG_JUNIT_PLUGIN = "org.junit";
   String PROJECT_CONTEXT = "project";
   String TEMPLATE_CONTEXT = "template";
   String BIN_DIR = "bin";
   String IDEA_SETTINGS_POSTFIX = ".eml";
   String ECLIPSE_JAR_PREFIX = "jar:file:/";
   String ECLIPSE_FILE_PREFIX = "file:/";
   String ATTRIBUTES_TAG = "attributes";
   String ATTRIBUTE_TAG = "attribute";
   String NAME_ATTR = "name";
   String VALUE_ATTR = "value";
   String DOT_CLASSPATH_EXT = "." + CLASSPATH_EXT;
   String DOT_PROJECT_EXT = "." + PROJECT_EXT;
   String FILE_PROTOCOL = "file:/";
   String PLATFORM_PROTOCOL = "platform:/";
   String JAR_PREFIX = "jar:";
   String JAVA_SDK_TYPE = "/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType";
   String LINKED_RESOURCES = "linkedResources";
   String LINK = "link";
   String JAVADOC_LOCATION = "javadoc_location";
   String DLL_LINK = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";
}
