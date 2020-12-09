// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.copyright;

import com.intellij.copyright.UpdateJavaFileCopyright;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.JavaOptions;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import java.util.List;

public class UpdateGroovyCopyrightsProvider extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdateJavaFileCopyright(project, module, file, options) {
      @Override
      protected boolean accept() {
        return getFile() instanceof GroovyFile;
      }

      @Override
      protected PsiElement[] getImportsList() {
        return ((GroovyFile)getFile()).getImportStatements();
      }

      @Override
      protected PsiElement getPackageStatement() {
        return ((GroovyFile)getFile()).getPackageDefinition();
      }

      @Override
      protected void checkCommentsForTopClass(PsiClass topClass, int location, List<PsiComment> comments) {
        if (!(topClass instanceof GroovyScriptClass)) {
          super.checkCommentsForTopClass(topClass, location, comments);
          return;
        }
        final GroovyFile containingFile = (GroovyFile)topClass.getContainingFile();

        PsiElement last = containingFile.getFirstChild();
        while (last != null && !(last instanceof GrStatement)) {
          last = last.getNextSibling();
        }
        checkComments(last, location == JavaOptions.LOCATION_BEFORE_CLASS, comments);
      }
    };
  }
}