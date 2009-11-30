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

/*
 * User: anna
 * Date: 30-Nov-2009
 */
package org.jetbrains.plugins.groovy.copyright;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightInstanceFactory;
import com.maddyhome.idea.copyright.psi.UpdateJavaFileCopyright;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

public class UpdateGroovyCopyrightInstanceFactory implements UpdateCopyrightInstanceFactory {
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdateJavaFileCopyright(project, module, file, options) {
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
    };
  }
}