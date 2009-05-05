package com.intellij.appengine.inspections;

import com.intellij.codeInspection.InspectionToolProvider;

/**
 * @author nik
 */
public class AppEngineInspectionsProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] {
        AppEngineForbiddenCodeInspection.class
    };
  }
}
