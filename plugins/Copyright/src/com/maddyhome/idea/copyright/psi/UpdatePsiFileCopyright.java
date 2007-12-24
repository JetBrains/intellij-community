package com.maddyhome.idea.copyright.psi;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class UpdatePsiFileCopyright extends AbstractUpdateCopyright
{
    protected UpdatePsiFileCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        super(project, module, root, options);

        PsiManager manager = PsiManager.getInstance(project);
        file = manager.findFile(root);
        FileType type = FileTypeUtil.getInstance().getFileTypeByFile(root);
        langOpts = options.getMergedOptions(type.getName());
    }

    public void prepare()
    {
        if (file == null)
        {
            logger.info("No file for root: " + getRoot());
            return;
        }

        if (accept())
        {
            scanFile();
        }
    }

    public void complete() throws IncorrectOperationException, Exception
    {
        if (file == null)
        {
            logger.info("No file for root: " + getRoot());
            return;
        }

        if (accept())
        {
            processActions();
        }
    }

    protected boolean accept()
    {
        return !(file instanceof PsiPlainTextFile);
    }

    protected abstract void scanFile();

    protected void checkComments(PsiElement first, PsiElement last, boolean commentHere)
    {
        List<PsiElement> comments = new ArrayList<PsiElement>();
        PsiElement elem = first;
        while (elem != last && elem != null)
        {
            if (elem instanceof PsiComment)
            {
                comments.add(elem);
                logger.debug("found comment");
            }

            elem = getNextSibling(elem);
        }

        try
        {
            ArrayList<CommentRange> found = new ArrayList<CommentRange>();
            Pattern pattern = Pattern.compile(langOpts.getKeyword());
            Document doc = FileDocumentManager.getInstance().getDocument(getFile().getVirtualFile());
            for (int i = 0; i < comments.size(); i++)
            {
                PsiElement comment = comments.get(i);
                String text = comment.getText();
                Matcher match = pattern.matcher(text);
                if (match.find())
                {
                    PsiElement firstComment = comment;
                    PsiElement lastComment = comment;
                    int sline = doc.getLineNumber(comment.getTextRange().getStartOffset());
                    int eline = doc.getLineNumber(comment.getTextRange().getEndOffset());
                    for (int j = i - 1; j >= 0; j--)
                    {
                        PsiElement cmt = comments.get(j);
                        if (doc.getLineNumber(cmt.getTextRange().getEndOffset()) == sline - 1)
                        {
                            firstComment = cmt;
                            sline = doc.getLineNumber(cmt.getTextRange().getStartOffset());
                        }
                        else
                        {
                            break;
                        }
                    }
                    for (i = i + 1; i < comments.size(); i++)
                    {
                        PsiElement cmt = comments.get(i);
                        if (doc.getLineNumber(cmt.getTextRange().getStartOffset()) == eline + 1)
                        {
                            lastComment = cmt;
                            eline = doc.getLineNumber(cmt.getTextRange().getEndOffset());
                        }
                        else
                        {
                            i--;
                            break;
                        }
                    }

                    found.add(new CommentRange(firstComment, lastComment));
                }
            }

            // Default insertion point to just before user chosen marker (package, import, class)
            PsiElement point = last;
            if (commentHere && comments.size() > 0 && langOpts.isRelativeBefore())
            {
                // Insert before first comment within this section of code.
                point = comments.get(0);
            }

            if (commentHere && found.size() == 1)
            {
                CommentRange range = found.get(0);
                // Is the comment in the right place?
                if ((langOpts.isRelativeBefore() && range.getFirst() == comments.get(0)) ||
                    (!langOpts.isRelativeBefore() && range.getLast() == comments.get(comments.size() - 1)))
                {
                    // Check to see if current copyright comment matches new one.
                    String newComment = getCommentText("", "");
                    resetCommentText();
                    String oldComment =
                        doc.getCharsSequence().subSequence(range.getFirst().getTextRange().getStartOffset(),
                        range.getLast().getTextRange().getEndOffset()).toString().trim();
                    if (newComment.trim().equals(oldComment))
                    {
                        if (!getLanguageOptions().isAddBlankAfter())
                        {
                            // TODO - do we need option to remove blank line after?
                            return; // Nothing to do since the comment is the same
                        }
                        else
                        {
                            PsiElement next = getNextSibling(range.getLast());
                            if (next instanceof PsiWhiteSpace && countNewline(next.getText()) > 1)
                            {
                                return;
                            }
                        }
                        point = range.getFirst();
                    }
                    else if (newComment.length() > 0)
                    {
                        int start = range.getFirst().getTextRange().getStartOffset();
                        int end = range.getLast().getTextRange().getEndOffset();
                        addAction(new CommentAction(CommentAction.ACTION_REPLACE, start, end));

                        return;
                    }
                }
            }

            for (CommentRange range : found)
            {
                // Remove the old copyright
                int start = range.getFirst().getTextRange().getStartOffset();
                int end = range.getLast().getTextRange().getEndOffset();
                // If this is the only comment then remove the whitespace after unless there is none before
                if (range.getFirst() == comments.get(0) && range.getLast() == comments.get(comments.size() - 1))
                {
                    int startLen = 0;
                    if (getPreviousSibling(range.getFirst()) instanceof PsiWhiteSpace)
                    {
                        startLen = countNewline(getPreviousSibling(range.getFirst()).getText());
                    }
                    int endLen = 0;
                    if (getNextSibling(range.getLast()) instanceof PsiWhiteSpace)
                    {
                        endLen = countNewline(getNextSibling(range.getLast()).getText());
                    }
                    if (startLen == 1 && getPreviousSibling(range.getFirst()).getTextRange().getStartOffset() > 0)
                    {
                        start = getPreviousSibling(range.getFirst()).getTextRange().getStartOffset();
                    }
                    else if (endLen > 0)
                    {
                        end = getNextSibling(range.getLast()).getTextRange().getEndOffset();
                    }
                }
                // If this is the last comment then remove the whitespace before the comment
                else if (range.getLast() == comments.get(comments.size() - 1))
                {
                    if (getPreviousSibling(range.getFirst()) instanceof PsiWhiteSpace &&
                        countNewline(getPreviousSibling(range.getFirst()).getText()) > 1)
                    {
                        start = getPreviousSibling(range.getFirst()).getTextRange().getStartOffset();
                    }
                }
                // If this is the first or middle comment then remove the whitespace after the comment
                else if (getNextSibling(range.getLast()) instanceof PsiWhiteSpace)
                {
                    end = getNextSibling(range.getLast()).getTextRange().getEndOffset();
                }

                addAction(new CommentAction(CommentAction.ACTION_DELETE, start, end));
            }

            // Finally add the comment if user chose this section.
            if (commentHere)
            {
                String suffix = "\n";
                if (point != last && getPreviousSibling(point) != null &&
                    getPreviousSibling(point) instanceof PsiWhiteSpace)
                {
                    suffix = getPreviousSibling(point).getText();
                    if (countNewline(suffix) == 1)
                    {
                        suffix = '\n' + suffix;
                    }
                }
                if (point != last && getPreviousSibling(point) == null)
                {
                    suffix = "\n\n";
                }
                if (getLanguageOptions().isAddBlankAfter() && countNewline(suffix) == 1)
                {
                    suffix += "\n";
                }
                String prefix = "";
                if (getPreviousSibling(point) != null)
                {
                    if (getPreviousSibling(point) instanceof PsiComment)
                    {
                        prefix = "\n\n";
                    }
                    if (getPreviousSibling(point) instanceof PsiWhiteSpace &&
                        getPreviousSibling(getPreviousSibling(point)) != null &&
                        getPreviousSibling(getPreviousSibling(point)) instanceof PsiComment)
                    {
                        String ws = getPreviousSibling(point).getText();
                        int cnt = countNewline(ws);
                        if (cnt == 1)
                        {
                            prefix = "\n";
                        }
                    }
                }

                addAction(new CommentAction(point == null ? 0 : point.getTextRange().getStartOffset(), prefix, suffix));
            }
        }
        catch (Exception e)
        {
            logger.error(e);
        }
    }

    protected PsiFile getFile()
    {
        return file;
    }

    protected LanguageOptions getLanguageOptions()
    {
        return langOpts;
    }

    protected void addAction(CommentAction action)
    {
        actions.add(action);
    }

    protected PsiElement getPreviousSibling(PsiElement element)
    {
        return element == null ? null : element.getPrevSibling();
    }

    protected PsiElement getNextSibling(PsiElement element)
    {
        return element == null ? null : element.getNextSibling();
    }

    protected void processActions() throws IncorrectOperationException
    {
        Application app = ApplicationManager.getApplication();
        app.runWriteAction(new Runnable()
        {
            public void run()
            {
                Document doc = FileDocumentManager.getInstance().getDocument(getRoot());
                for (CommentAction action : actions)
                {
                    int start = action.getStart();
                    int end = action.getEnd();

                    switch (action.getType())
                    {
                        case CommentAction.ACTION_INSERT:
                            String comment = getCommentText(action.getPrefix(), action.getSuffix());
                            if (comment.length() > 0)
                            {
                                doc.insertString(start, comment);
                            }
                            break;
                        case CommentAction.ACTION_REPLACE:
                            doc.replaceString(start, end, getCommentText("", ""));
                            break;
                        case CommentAction.ACTION_DELETE:
                            doc.deleteString(start, end);
                            break;
                    }
                }
            }
        });
    }

    private static class CommentRange
    {
        public CommentRange(PsiElement first, PsiElement last)
        {
            this.first = first;
            this.last = last;
        }

        public PsiElement getFirst()
        {
            return first;
        }

        public PsiElement getLast()
        {
            return last;
        }

        private PsiElement first;
        private PsiElement last;
    }

    protected static class CommentAction implements Comparable<CommentAction>
    {
        public static final int ACTION_INSERT = 1;
        public static final int ACTION_REPLACE = 2;
        public static final int ACTION_DELETE = 3;

        public CommentAction(int pos, String prefix, String suffix)
        {
            type = ACTION_INSERT;
            start = pos;
            end = pos;
            this.prefix = prefix;
            this.suffix = suffix;
        }

        public CommentAction(int type, int start, int end)
        {
            this.type = type;
            this.start = start;
            this.end = end;
        }

        public int getType()
        {
            return type;
        }

        public int getStart()
        {
            return start;
        }

        public int getEnd()
        {
            return end;
        }

        public String getPrefix()
        {
            return prefix;
        }

        public String getSuffix()
        {
            return suffix;
        }

        public int compareTo(CommentAction object)
        {
            int s = object.getStart();
            int diff = s - start;
            if (diff == 0)
            {
                diff = type == ACTION_INSERT ? 1 : -1;
            }

            return diff;
        }

        private int type;
        private int start;
        private int end;
        private String prefix = null;
        private String suffix = null;
    }

    private PsiFile file;
    private LanguageOptions langOpts;
    private TreeSet<CommentAction> actions = new TreeSet<CommentAction>();

    private static Logger logger = Logger.getInstance(UpdatePsiFileCopyright.class.getName());
}
