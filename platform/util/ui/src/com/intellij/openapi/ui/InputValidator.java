// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.NlsSafe;

import javax.swing.*;

/**
 * The validator for input dialogs.
 *
 * @see Messages#showInputDialog(String, String, Icon, String, InputValidator)
 * @see Messages#showInputDialog(java.awt.Component, String, String, Icon, String, InputValidator)
 * @see Messages#showInputDialog(com.intellij.openapi.project.Project, String, String, Icon, String, InputValidator)
 * @see Messages#showEditableChooseDialog(String, String, Icon, String[], String, InputValidator)
 */
public interface InputValidator {
  /**
   * Checks whether the {@code inputString} is valid. It is invoked each time
   * input changes.
   *
   * @param inputString the input to check
   * @return true if input string is valid
   */
  boolean checkInput(@NlsSafe String inputString);

  /**
   * This method is invoked just before message dialog is closed with OK code.
   * If {@code false} is returned then then the message dialog will not be closed.
   *
   * @param inputString the input to check
   * @return true if the dialog could be closed, false otherwise.
   */
  boolean canClose(@NlsSafe String inputString);
}
