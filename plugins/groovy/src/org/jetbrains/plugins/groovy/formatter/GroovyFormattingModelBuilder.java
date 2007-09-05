/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.formatter;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.grails.lang.gsp.psi.groovy.api.GspOuterGroovyElement;
import org.jetbrains.plugins.grails.lang.gsp.psi.gsp.api.GspFile;

/**
 * @author ilyas
 */
public class GroovyFormattingModelBuilder implements FormattingModelBuilder {
  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    ASTNode node = element.getNode();
    assert node != null;
    PsiFile containingFile = element.getContainingFile();
    if (element instanceof GspOuterGroovyElement) {
      containingFile = ((GspFile) element.getContainingFile()).getGroovyLanguageRoot();
    }
    return FormattingModelProvider.createFormattingModelForPsiFile(containingFile,
        new GroovyBlock(node, null, Indent.getAbsoluteNoneIndent(), null, settings), settings);
  }
}
