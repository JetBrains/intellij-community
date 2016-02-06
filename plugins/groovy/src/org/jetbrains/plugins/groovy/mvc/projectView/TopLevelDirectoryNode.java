/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
    return super.equals(object) && Comparing.equal(myTitle, ((TopLevelDirectoryNode) object).myTitle);
  }

  @Override
  protected void updateImpl(PresentationData data) {
    data.setPresentableText(myTitle);
    data.setIcon(myIcon);
  }

}
