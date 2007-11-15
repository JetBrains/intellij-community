/*
 * @author max
 */
package com.intellij.lang.ant;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;

public class AntNamesValidator implements NamesValidator {
  public boolean isKeyword(String name, Project project) {
    return false;
  }

  public boolean isIdentifier(String name, Project project) {
    return true;
  }
}