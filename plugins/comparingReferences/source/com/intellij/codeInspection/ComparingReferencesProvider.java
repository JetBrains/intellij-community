package com.intellij.codeInspection;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * @author max
 */
public class ComparingReferencesProvider implements InspectionToolProvider, ApplicationComponent {
  public Class[] getInspectionClasses() {
    return new Class[] {ComparingReferencesInspection.class};
  }

  public String getComponentName() {
    return "ComparingReferencesProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {

  }
}
