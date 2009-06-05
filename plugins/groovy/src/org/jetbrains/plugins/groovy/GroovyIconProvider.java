/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.script.GroovyScriptDetector;
import org.jetbrains.plugins.groovy.extensions.script.ScriptDetectorRegistry;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyIconProvider extends IconProvider implements DumbAware {

  @Nullable
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)element;
      for (GroovyScriptDetector detector : ScriptDetectorRegistry.getInstance().getScriptDetectors()) {
        if (detector.isSpecificScriptFile(file)) {
          return detector.getScriptIcon();
        }
      }
      if (!file.isScript()) {
        GrTypeDefinition[] typeDefinitions = file.getTypeDefinitions();
        if (typeDefinitions.length > 0) {
          return typeDefinitions[0].getIcon(flags);
        }
      }

      return GroovyFileType.GROOVY_LOGO;
    }

    return null;
  }

}
