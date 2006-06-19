/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.comment;

import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ChangeToEndOfLineCommentIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new CStyleCommentPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiManager manager = element.getManager();
        final PsiElement parent = element.getParent();
        assert parent != null;
        final PsiElementFactory factory = manager.getElementFactory();
        final String commentText = element.getText();
        final PsiElement whitespace = element.getNextSibling();
        final String text = commentText.substring(2, commentText.length() - 2);
        final String[] lines = text.split("\n");
        final String newComment = buildCommentString(lines);
        final PsiCodeFragment fragment =
                factory.createCodeBlockCodeFragment(newComment,
                        null, false);
        final PsiElement firstChild = fragment.getFirstChild();
        final PsiElement lastChild = fragment.getLastChild();
        parent.addRangeBefore(firstChild, lastChild, element);
        if (whitespace != null) {
            whitespace.delete();
        }
        element.delete();
    }

    private static String buildCommentString(String[] lines) {
        int lastNonEmtpyLine = -1;
        for (int i = lines.length - 1; i >= 0 && lastNonEmtpyLine == -1; i--) {
            final String line = lines[i].trim();
            if (line.length() != 0) {
                lastNonEmtpyLine = i;
            }
        }
        if (lastNonEmtpyLine == -1) {
            return "//\n";
        }
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i <= lastNonEmtpyLine; i++) {
            final String line = lines[i];
            final String trimmedLine = line.trim();
            if (buffer.length() != 0 || trimmedLine.length() != 0) {
                buffer.append("//");
                if (line.startsWith(" *")) {
                    buffer.append(line.substring(2));
                } else if (line.length() > 0 && line.charAt(0) == '*') {
                    buffer.append(line.substring(1));
                } else {
                    buffer.append(line);
                }
                buffer.append('\n');
            }
        }
        return buffer.toString();
    }
}