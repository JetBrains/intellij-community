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
package org.jetbrains.plugins.groovy;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.swing.*;

/**
 * @author peter
 */
public class GroovyIconProvider extends IconProvider {

  @Nullable
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)element;
      if (!file.isScript()) {
        GrTypeDefinition[] typeDefinitions = file.getTypeDefinitions();
        if (typeDefinitions.length > 0) {
          return typeDefinitions[0].getIcon(flags);
        }
        return JetgroovyIcons.Groovy.Groovy_16x16;
      }

      return GroovyScriptTypeDetector.getScriptType(file).getScriptIcon();
    }

    return null;
  }

}
