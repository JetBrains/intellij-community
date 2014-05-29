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
package org.jetbrains.plugins.groovy.lang;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleManager;

public class CoreGroovyCodeStyleManager extends GroovyCodeStyleManager {
  @NotNull
  @Override
  public GrImportStatement addImport(@NotNull GroovyFile psiFile, @NotNull GrImportStatement statement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeImport(@NotNull GroovyFileBase psiFile, @NotNull GrImportStatement importStatement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }
}
