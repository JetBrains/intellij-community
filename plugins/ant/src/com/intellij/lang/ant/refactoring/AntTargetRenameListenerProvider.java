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
