// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

/**
 * @author peter
 */
public class TopLevelDirectoryNode extends AbstractFolderNode {
  private final String myTitle;
  private final Icon myIcon;

  public TopLevelDirectoryNode(@NotNull Module module,
                               @NotNull PsiDirectory directory,
                               ViewSettings viewSettings,
                               String title,
                               Icon icon,
                               int weight) {
    super(module, directory, directory.getName(), viewSettings, weight);
    myTitle = title;
    myIcon = icon;
  }

  @Override
  public boolean equals(Object object) {
    return super.equals(object) && Objects.equals(myTitle, ((TopLevelDirectoryNode)object).myTitle);
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    data.setPresentableText(myTitle);
    data.setIcon(myIcon);
  }

}
