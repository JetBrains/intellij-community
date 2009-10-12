/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.refactoring;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.lang.ant.config.impl.AntConfigurationImpl;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 14, 2009
 */
public class AntTargetRenameListenerProvider implements RefactoringElementListenerProvider {
  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof AntTarget) {
      final String oldName = ((AntTarget)element).getName();
      return new RefactoringElementListener() {
        public void elementMoved(@NotNull PsiElement newElement) {
        }

        public void elementRenamed(@NotNull PsiElement newElement) {
          final String newName = ((AntTarget)newElement).getName();
          if (!Comparing.equal(oldName, newName)) {
            final AntConfiguration configuration = AntConfiguration.getInstance(newElement.getProject());
            ((AntConfigurationImpl)configuration).handleTargetRename(oldName, newName);

            for (BeforeRunTaskProvider provider : Extensions.getExtensions(AntBeforeRunTaskProvider.EXTENSION_POINT_NAME, newElement.getProject())) {
              if (AntBeforeRunTaskProvider.ID.equals(provider.getId())) {
                ((AntBeforeRunTaskProvider)provider).handleTargetRename(oldName, newName);
                break;
              }
            }
          }
        }
      };
    }
    return null;
  }
}
