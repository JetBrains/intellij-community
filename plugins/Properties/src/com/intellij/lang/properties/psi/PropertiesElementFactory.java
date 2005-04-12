package com.intellij.lang.properties.psi;

import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.lang.properties.PropertiesSupportLoader;

/**
 * @author cdr
 */
public class PropertiesElementFactory {
  public static Property createProperty(Project project, String name, String value) {
    ParserDefinition def = PropertiesSupportLoader.FILE_TYPE.getLanguage().getParserDefinition();
    final PropertiesFile dummyFile = (PropertiesFile)def.createFile(project, "dummy." + PropertiesSupportLoader.FILE_TYPE.getDefaultExtension(), name + "="+value);
    final Property property = dummyFile.getProperties()[0];
    return property;
  }
  public static PropertyKey createKey(Project project, String name) {
    final Property property = createProperty(project, name, "xxx");
    return property.getKey();
  }
  public static PropertyValue createValue(Project project, String value) {
    final Property property = createProperty(project, "xxx", value);
    return property.getValue();
  }
}
