package com.intellij.lang.ant;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.lang.ant.validation.AntDuplicateImportedTargetsInspection;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.lang.ant.validation.AntMissingPropertiesFileInspection;

/**
 * @author yole
 */
public class AntInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[]{AntDuplicateTargetsInspection.class, AntDuplicateImportedTargetsInspection.class,
      AntMissingPropertiesFileInspection.class};
  }
}
