/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class XsltFormattingModelBuilder implements CustomFormattingModelBuilder {
    private final FormattingModelBuilder myBuilder;

    public XsltFormattingModelBuilder(FormattingModelBuilder builder) {
        myBuilder = builder;
    }

    public boolean isEngagedToFormat(PsiElement context) {
        final PsiFile file = context.getContainingFile();
        return file != null && (file.getFileType() == StdFileTypes.XML
                && file.getLanguage() == StdLanguages.XML);
    }

    @Nullable
    public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
        // TODO
        return null;
    }

    @NotNull
    public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
        return new XslTextFormattingModel(myBuilder.createModel(element, settings), settings);
    }
}
