package com.intellij.lang.properties.psi;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.properties.PropertiesSupportLoader;
import com.intellij.openapi.project.Project;

/**
 * @author cdr
 */
public class PropertiesElementFactory {
  public static Property createProperty(Project project, String name, String value) {
    ParserDefinition def = PropertiesSupportLoader.FILE_TYPE.getLanguage().getParserDefinition();
    final PropertiesFile dummyFile = (PropertiesFile)def.createFile(project, "dummy." + PropertiesSupportLoader.FILE_TYPE.getDefaultExtension(), name + "="+value);
    return dummyFile.getProperties()[0];
  }
}
