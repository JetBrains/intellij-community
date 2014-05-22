/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 30-Nov-2009
 */
package org.jetbrains.plugins.groovy.copyright;

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
import com.maddyhome.idea.copyright.psi.UpdateJavaFileCopyright;
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
      protected void checkCommentsForTopClass(PsiClass topclass, int location, List<PsiComment> comments) {
        if (!(topclass instanceof GroovyScriptClass)) {
          super.checkCommentsForTopClass(topclass, location, comments);
          return;
        }
        final GroovyFile containingFile = (GroovyFile)topclass.getContainingFile();

        PsiElement last = containingFile.getFirstChild();
        while (last != null && !(last instanceof GrStatement)) {
          last = last.getNextSibling();
        }
        checkComments(last, location == JavaOptions.LOCATION_BEFORE_CLASS, comments);
      }
    };
  }
}