/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.util.GrFileIndexUtil;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighterFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {
  public GrReferenceHighlighterFactory(Project project, TextEditorHighlightingPassRegistrar registrar) {
    super(project);
    registrar.registerTextEditorHighlightingPass(this, null, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!isSpecificScriptFile(file) && !GrFileIndexUtil.isGroovySourceFile(file)) return null;
    return new GrReferenceHighlighter(editor.getDocument(), (GroovyFileBase)file);
  }

  private static boolean isSpecificScriptFile(@NotNull PsiFile file) {
    if (!(file instanceof GroovyFile)) return false;
    if (!((GroovyFile)file).isScript()) return false;

    for (GroovyScriptTypeDetector detector : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile((GroovyFile)file)) {
        return true;
      }
    }
    return false;
  }

}
