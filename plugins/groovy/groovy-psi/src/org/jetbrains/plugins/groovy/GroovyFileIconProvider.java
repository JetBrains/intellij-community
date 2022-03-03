// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.ElementBase;
import com.intellij.ui.IconManager;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

import javax.swing.*;

final class GroovyFileIconProvider implements FileIconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile virtualFile, @Iconable.IconFlags int flags, @Nullable Project project) {
    if (project == null || !FileTypeRegistry.getInstance().isFileOfType(virtualFile, GroovyFileType.GROOVY_FILE_TYPE)) return null;
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
             : JetgroovyIcons.Groovy.GroovyFile;
    }
    else {
      icon = JetgroovyIcons.Groovy.Groovy_outsideSources;
    }
    return IconManager.getInstance().createLayeredIcon(psiFile, icon, ElementBase.transformFlags(psiFile, flags));
  }
}
