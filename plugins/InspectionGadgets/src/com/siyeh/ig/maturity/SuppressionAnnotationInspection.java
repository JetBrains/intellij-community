/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.InspectionGadgetsBundle;

public class SuppressionAnnotationInspection extends ClassInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("inspection.suppression.annotation.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.MATURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("inspection.suppression.annotation.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SuppressionAnnotationVisitor();
    }

    private static class SuppressionAnnotationVisitor
            extends BaseInspectionVisitor{
        @SuppressWarnings({"HardCodedStringLiteral"})
        public void visitComment(PsiComment comment){
            super.visitComment(comment);
            final String commentText = comment.getText();
            final IElementType tokenType = comment.getTokenType();
            if(!tokenType.equals(JavaTokenType.END_OF_LINE_COMMENT)
               && !tokenType.equals(JavaTokenType.C_STYLE_COMMENT)){
                return;
            }
            final String strippedComment = commentText.substring(2).trim();
            if(strippedComment.startsWith("noinspection")){
                registerError(comment);
            }
        }

        @SuppressWarnings({"HardCodedStringLiteral"})
        public void visitAnnotation(PsiAnnotation annotation){
            super.visitAnnotation(annotation);
            final PsiJavaCodeReferenceElement reference =
                    annotation.getNameReferenceElement();
            if(reference == null)
            {
                return;
            }
            final String text = reference.getText();

            if("SuppressWarnings".equals(text) ||
                    "java.lang.SuppressWarnings".equals(text)){
                registerError(annotation);
            }
        }
    }
}
