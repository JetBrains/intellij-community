/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.quickedit;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xml.util.XmlStringUtil;
import com.intellij.xml.util.XmlUtil;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Quick Edit Language" intention action that provides a popup which shows an injected language
 * fragment's complete prefix and suffix in non-editable areas and allows to edit the fragment
 * without having to consider any additional escaping rules (e.g. when editing regexes in String
 * literals).
 *
 * This is a bit experimental because it doesn't play very well with some quickfixes, such as the
 * JavaScript's "Create Method/Function" one which opens another editor window. Though harmless,
 * this is quite confusing.
 *
 * I wonder if such QuickFixes should try to get an Editor from the DataContext
 * (see {@link QuickEditEditor.MyPanel#getData(java.lang.String)}) instead of using the "tactical nuke"
 * com.intellij.openapi.fileEditor.FileEditorManager#openTextEditor(com.intellij.openapi.fileEditor.OpenFileDescriptor, boolean). 
 */
public class QuickEditAction implements IntentionAction {
    private static final Pattern SPACE_PATTERN = Pattern.compile("^(\\s*)(.*?)(\\s*)$");

    @NotNull
    public String getText() {
        return "Quick Edit Language";
    }

    @NotNull
    public String getFamilyName() {
        return "Quick Edit";
    }

    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (false && editor instanceof EditorWindow) {
            final PsiElement context = file.getContext();
            if (context == null) {
                return false;
            }
            final PsiElement firstChild = context.getFirstChild();
            if (firstChild != null) {
                final ASTNode node = firstChild.getNode();
                if (node == null || node.getElementType() == XmlElementType.XML_CDATA) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public void invoke(@NotNull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
        final PsiElement context = file.getContext();
        final Document document = editor.getDocument();
        final DocumentWindow docRange = ((DocumentWindow)document);

        final boolean isInsideStringLiteral = context instanceof PsiLiteralExpression;
        final boolean isInsideXml = context instanceof XmlElement;
        final TextRange textRange = docRange.getHostRange(0);

        final String prefix = "";//docRange.getPrefix();
        final String suffix = "";//docRange.getSuffix();

        final String fullText = getUnescapedText(docRange.getDelegate().getText().substring(textRange.getStartOffset(), textRange.getEndOffset()), isInsideStringLiteral, isInsideXml);
        final Matcher matcher = SPACE_PATTERN.matcher(fullText);
        final String prefixSpace;
        final String suffixSpace;
        final String text;
        if (matcher.matches()) {
            prefixSpace = matcher.group(1);
            text = prefix + matcher.group(2) + suffix;
            suffixSpace = matcher.group(3);
        } else {
            text = prefix + fullText + suffix;
            prefixSpace = "";
            suffixSpace = "";
        }

        final FileType ft = file.getLanguage().getAssociatedFileType();
        final FileType fileType = ft != null ? ft : file.getFileType();

        final PsiFileFactory factory = PsiFileFactory.getInstance(project);
        final PsiFile file2 = factory.createFileFromText("dummy." + fileType.getDefaultExtension(), fileType, text, LocalTimeCounter.currentTime(), true);
        final Document d = PsiDocumentManager.getInstance(project).getDocument(file2);
        assert d != null;

        final RangeMarker prefixRange;
        if (prefix.length() > 0) {
            prefixRange = d.createGuardedBlock(0, prefix.length());
            prefixRange.setGreedyToLeft(true);
        } else {
            prefixRange = null;
        }
        final RangeMarker suffixRange;
        if (suffix.length() > 0) {
            suffixRange = d.createGuardedBlock(text.length() - suffix.length(), text.length());
            suffixRange.setGreedyToRight(true);
        } else {
            suffixRange = null;
        }

        final QuickEditEditor e = new QuickEditEditor(d, project, fileType, new QuickEditEditor.QuickEditSaver() {
            public void save(final String text) {
                String t = text;

                if (prefixRange != null) {
                    assert prefixRange.getStartOffset() == 0;
                    t = t.substring(prefixRange.getEndOffset());
                }
                if (suffixRange != null) {
                    final int length = suffixRange.getEndOffset() - suffixRange.getStartOffset();
                    t = t.substring(0, t.length() - length);
                }
                final String s = isInsideStringLiteral ?
                        StringUtil.escapeStringCharacters(t) :
                        (isInsideXml ? XmlStringUtil.escapeString(t) : t);

                document.setText(prefix + prefixSpace + s + suffixSpace + suffix);
            }
        });
        e.getEditor().getCaretModel().moveToOffset(prefix.length());

        // this doesn't seem to work
        e.getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);

        // Using the popup doesn't seem to be a good idea because there's no completion possible inside it: When the
        // completion popup closes, the quickedit popup is gone as well - but I like the movable and resizable popup :(
        final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(e.getComponent(), e.getPreferredFocusedComponent());
        builder.setMovable(true);
        builder.setResizable(true);
        builder.setRequestFocus(true);
        builder.setTitle("<html>Edit <b>" + fileType.getName() + "</b> Fragment</html>");
        builder.setCancelCallback(new Computable<Boolean>() {
            public Boolean compute() {
                e.setCancel(true);
                e.uninstall();
                return Boolean.TRUE;
            }
        });

        final JBPopup popup = builder.createPopup();
        e.install(popup);

        popup.showInBestPositionFor(((EditorWindow)editor).getDelegate());
    }

    private static String getUnescapedText(String text, boolean insideStringLiteral, boolean insideXml) {
        return insideStringLiteral ?
                StringUtil.unescapeStringCharacters(text) :
                (insideXml ? XmlUtil.unescape(text) : text);
    }

    public boolean startInWriteAction() {
        return false;
    }
}
