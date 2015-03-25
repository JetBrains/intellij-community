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
package org.jetbrains.plugins.groovy;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.ElementBase;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

import javax.swing.*;

public class GroovyFileIconProvider implements FileIconProvider {

  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile virtualFile, @Iconable.IconFlags int flags, @Nullable Project project) {
    if (project == null || virtualFile.getFileType() != GroovyFileType.GROOVY_FILE_TYPE) return null;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
    if (!(psiFile instanceof GroovyFile)) return null;
    final GroovyFile file = (GroovyFile)psiFile;
    final Icon icon;
    if (file.isScript()) {
      icon = GroovyScriptTypeDetector.getIcon(file);
    }
    else if (GrFileIndexUtil.isGroovySourceFile(file)) {
      final GrTypeDefinition[] typeDefinitions = file.getTypeDefinitions();
      icon = typeDefinitions.length > 0
             ? typeDefinitions[0].getIcon(flags)
             : JetgroovyIcons.Groovy.Groovy_16x16;
    }
    else {
      icon = JetgroovyIcons.Groovy.Groovy_outsideSources;
    }
    return ElementBase.createLayeredIcon(psiFile, icon, ElementBase.transformFlags(psiFile, flags));
  }
}
