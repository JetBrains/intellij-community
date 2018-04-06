// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.checkout;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;

import java.io.File;

/**
 * @author yole
 */
public class ProjectCheckoutListener implements CheckoutListener {
  public ProjectCheckoutListener() { }

  @Override
  public boolean processCheckedOutDirectory(Project project, File directory) {
    File[] files = directory.listFiles((dir, name) -> dir.isFile() && name.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION));
    if (files != null && files.length > 0) {
      String message = VcsBundle.message("checkout.open.project.prompt", getProductNameWithArticle(), files[0].getPath());
      if (Messages.showYesNoDialog(project, message, VcsBundle.message("checkout.title"), Messages.getQuestionIcon()) == Messages.YES) {
        ProjectUtil.openProject(files[0].getPath(), project, false);
      }
      return true;
    }
    return false;
  }

  @Override
  public void processOpenedProject(Project lastOpenedProject) { }

  static String getProductNameWithArticle() {
    // examples: "to create an IntelliJ IDEA project" (full product name is ok), "to create a PyCharm project"
    String productName = ApplicationNamesInfo.getInstance().getFullProductName();
    String article = StringUtil.isVowel(Character.toLowerCase(productName.charAt(0))) ? "an " : "a ";
    return article + productName;
  }
}