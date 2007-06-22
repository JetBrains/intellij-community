package org.jetbrains.idea.eclipse;

import org.jetbrains.annotations.NonNls;

public interface EclipseXml {
  @NonNls String UNNAMED_PROJECT = "unnamed";
  @NonNls String PROJECT_FILE = ".project";
  @NonNls String NAME_TAG = "name";
  @NonNls String CLASSPATH_FILE = ".classpath";
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
}
