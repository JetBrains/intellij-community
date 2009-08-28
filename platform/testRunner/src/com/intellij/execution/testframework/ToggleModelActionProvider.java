/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.execution.testframework;

import com.intellij.openapi.extensions.ExtensionPointName;

public interface ToggleModelActionProvider {
  ExtensionPointName<ToggleModelActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.testActionProvider");

  ToggleModelAction createToggleModelAction(TestConsoleProperties properties);

}