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
package com.intellij.lang.ant.validation;

import com.intellij.lang.ant.AntSupport;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.openapi.editor.HectorComponentPanel;
import com.intellij.openapi.editor.HectorComponentPanelsProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 12, 2008
 */
public class AntHectorPanelProvider implements HectorComponentPanelsProvider{
  public HectorComponentPanel createConfigurable(@NotNull final PsiFile file) {
    final AntFile antFile = AntSupport.getAntFile(file);
    if (antFile == null) {
      return null;
    }
    return new AntHectorConfigurable(antFile);
  }
}
