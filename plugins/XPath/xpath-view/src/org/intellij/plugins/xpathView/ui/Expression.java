/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;


public class Expression {
    private final PsiFile myFile;
    private final LanguageFileType myFileType;
    private final PsiDocumentManager myDocMgr;
    private final Document myDocument;

    Expression(PsiFile file, LanguageFileType fileType) {
        myFile = file;
        myFileType = fileType;
        myDocMgr = PsiDocumentManager.getInstance(file.getProject());
        myDocument = myDocMgr.getDocument(file);
    }

    public PsiFile getFile() {
        return myFile;
    }

    public LanguageFileType getFileType() {
        return myFileType;
    }

    public String getExpression() {
        myDocMgr.commitDocument(myDocument);
        final String text = myFile.getText();
        assert text.equals(myDocument.getText());
        return text;
    }

    static Expression create(Project project, LanguageFileType fileType) {
        return create(project, fileType, "");
    }

    public static Expression create(Project project, LanguageFileType fileType, String expression) {
        final PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("dummy.xpath", fileType, expression, LocalTimeCounter.currentTime(), true);
        return new Expression(file, fileType);
    }
}