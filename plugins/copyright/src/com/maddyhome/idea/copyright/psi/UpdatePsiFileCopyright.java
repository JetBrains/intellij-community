/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.psi;

import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class UpdatePsiFileCopyright extends AbstractUpdateCopyright {
  private static final Logger LOG = Logger.getInstance("#" + UpdatePsiFileCopyright.class.getName());
  private final CopyrightProfile myOptions;

  protected UpdatePsiFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
    super(project, module, root, options);
    myOptions = options;

    PsiManager manager = PsiManager.getInstance(project);
    file = manager.findFile(root);
    FileType type = FileTypeUtil.getInstance().getFileTypeByFile(root);
    langOpts = CopyrightManager.getInstance(project).getOptions().getMergedOptions(type.getName());
  }

  @Override
  public void prepare() {
    if (file == null) {
      logger.info("No file for root: " + getRoot());
      return;
    }

    if (accept()) {
      scanFile();
    }
  }

  @Override
  public void complete() throws Exception {
    complete(true);
  }

  public void complete(boolean allowReplacement) throws Exception {
    if (file == null) {
      logger.info("No file for root: " + getRoot());
      return;
    }

    if (accept()) {
      processActions(allowReplacement);
    }
  }

  protected boolean accept() {
    return !(file instanceof PsiPlainTextFile);
  }

  protected abstract void scanFile();

  protected void checkComments(PsiElement first, PsiElement last, boolean commentHere) {
    List<PsiComment> comments = new ArrayList<>();
    collectComments(first, last, comments);
    checkComments(last, commentHere, comments);
  }

  protected void collectComments(PsiElement first, PsiElement last, List<PsiComment> comments) {
    if (first == last && first instanceof PsiComment) {
      comments.add((PsiComment)first);
      return;
    }
    PsiElement elem = first;
    while (elem != last && elem != null) {
      if (elem instanceof PsiComment) {
        comments.add((PsiComment)elem);
        logger.debug("found comment");
      }

      elem = getNextSibling(elem);
    }
  }

  protected void checkComments(PsiElement last, boolean commentHere, List<PsiComment> comments) {
    try {
      final String keyword = myOptions.getKeyword();
      final LinkedHashSet<CommentRange> found = new LinkedHashSet<>();
      Document doc = null;
      if (!StringUtil.isEmpty(keyword)) {
        Pattern pattern = Pattern.compile(StringUtil.escapeToRegexp(keyword), Pattern.CASE_INSENSITIVE);
        doc = FileDocumentManager.getInstance().getDocument(getFile().getVirtualFile());
        for (int i = 0; i < comments.size(); i++) {
          PsiComment comment = comments.get(i);
          String text = comment.getText();
          Matcher match = pattern.matcher(text);
          if (match.find()) {
            found.add(getLineCopyrightComments(comments, doc, i, comment));
          }
        }
      }

      // Default insertion point to just before user chosen marker (package, import, class)
      PsiElement point = last;
      if (commentHere && !comments.isEmpty() && langOpts.isRelativeBefore()) {
        // Insert before first comment within this section of code.
        point = comments.get(0);
      }

      if (commentHere && found.size() == 1) {
        CommentRange range = found.iterator().next();
        // Is the comment in the right place?
        if (langOpts.isRelativeBefore() && range.getFirst() == comments.get(0) ||
            !langOpts.isRelativeBefore() && range.getLast() == comments.get(comments.size() - 1)) {
          // Check to see if current copyright comment matches new one.
          String newComment = getCommentText("", "");
          resetCommentText();
          String oldComment = doc.getCharsSequence()
            .subSequence(range.getFirst().getTextRange().getStartOffset(), range.getLast().getTextRange().getEndOffset()).toString().trim();
          if (!StringUtil.isEmptyOrSpaces(myOptions.getAllowReplaceKeyword()) &&
              !oldComment.contains(myOptions.getAllowReplaceKeyword())) {
            return;
          }
          if (newComment.trim().equals(oldComment)) {
            if (!getLanguageOptions().isAddBlankAfter()) {
              // TODO - do we need option to remove blank line after?
              return; // Nothing to do since the comment is the same
            }
            PsiElement next = getNextSibling(range.getLast());
            if (next != null) {
              final String text = next.getText();
              if (StringUtil.isEmptyOrSpaces(text) && countNewline(text) > 1) {
                return;
              }
            }
            point = range.getFirst();
          }
          else if (!newComment.isEmpty()) {
            int start = range.getFirst().getTextRange().getStartOffset();
            int end = range.getLast().getTextRange().getEndOffset();
            addAction(new CommentAction(CommentAction.ACTION_REPLACE, start, end));

            return;
          }
        }
      }

      for (CommentRange range : found) {
        // Remove the old copyright
        int start = range.getFirst().getTextRange().getStartOffset();
        int end = range.getLast().getTextRange().getEndOffset();
        // If this is the only comment then remove the whitespace after unless there is none before
        if (range.getFirst() == comments.get(0) && range.getLast() == comments.get(comments.size() - 1)) {
          int startLen = 0;
          if (getPreviousSibling(range.getFirst()) instanceof PsiWhiteSpace) {
            startLen = countNewline(getPreviousSibling(range.getFirst()).getText());
          }
          int endLen = 0;
          if (getNextSibling(range.getLast()) instanceof PsiWhiteSpace) {
            endLen = countNewline(getNextSibling(range.getLast()).getText());
          }
          if (startLen == 1 && getPreviousSibling(range.getFirst()).getTextRange().getStartOffset() > 0) {
            start = getPreviousSibling(range.getFirst()).getTextRange().getStartOffset();
          }
          else if (endLen > 0) {
            end = getNextSibling(range.getLast()).getTextRange().getEndOffset();
          }
        }
        // If this is the last comment then remove the whitespace before the comment
        else if (range.getLast() == comments.get(comments.size() - 1)) {
          if (getPreviousSibling(range.getFirst()) instanceof PsiWhiteSpace &&
              countNewline(getPreviousSibling(range.getFirst()).getText()) > 1) {
            start = getPreviousSibling(range.getFirst()).getTextRange().getStartOffset();
          }
        }
        // If this is the first or middle comment then remove the whitespace after the comment
        else if (getNextSibling(range.getLast()) instanceof PsiWhiteSpace) {
          end = getNextSibling(range.getLast()).getTextRange().getEndOffset();
        }

        addAction(new CommentAction(CommentAction.ACTION_DELETE, start, end));
      }

      // Finally add the comment if user chose this section.
      if (commentHere) {
        String suffix = "\n";
        if (point != last && getPreviousSibling(point) != null && getPreviousSibling(point) instanceof PsiWhiteSpace) {
          suffix = getPreviousSibling(point).getText();
          if (countNewline(suffix) == 1) {
            suffix = '\n' + suffix;
          }
        }
        if (point != last && getPreviousSibling(point) == null) {
          suffix = "\n\n";
        }
        if (getLanguageOptions().isAddBlankAfter() && countNewline(suffix) == 1) {
          suffix += "\n";
        }
        String prefix = "";
        if (getPreviousSibling(point) != null) {
          if (getPreviousSibling(point) instanceof PsiComment) {
            prefix = "\n\n";
          }
          if (getPreviousSibling(point) instanceof PsiWhiteSpace &&
              getPreviousSibling(getPreviousSibling(point)) != null &&
              getPreviousSibling(getPreviousSibling(point)) instanceof PsiComment) {
            String ws = getPreviousSibling(point).getText();
            int cnt = countNewline(ws);
            if (cnt == 1) {
              prefix = "\n";
            }
          }
        }

        int pos = 0;
        if (point != null) {
          final TextRange textRange = point.getTextRange();
          if (textRange != null) {
            pos = textRange.getStartOffset();
          }
        }
        addAction(new CommentAction(pos, prefix, suffix));
      }
    }
    catch (PatternSyntaxException ignore) {
    }
    catch (Exception e) {
      logger.error(e);
    }
  }

  private static CommentRange getLineCopyrightComments(List<PsiComment> comments, Document doc, int i, PsiComment comment) {
    PsiElement firstComment = comment;
    PsiElement lastComment = comment;
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(PsiUtilCore.findLanguageFromElement(comment));
    if (isLineComment(commenter, comment, doc)) {
      int sline = doc.getLineNumber(comment.getTextRange().getStartOffset());
      int eline = doc.getLineNumber(comment.getTextRange().getEndOffset());
      for (int j = i - 1; j >= 0; j--) {
        PsiComment cmt = comments.get(j);

        if (isLineComment(commenter, cmt, doc) && doc.getLineNumber(cmt.getTextRange().getEndOffset()) == sline - 1) {
          firstComment = cmt;
          sline = doc.getLineNumber(cmt.getTextRange().getStartOffset());
        }
        else {
          break;
        }
      }
      for (int j = i + 1; j < comments.size(); j++) {
        PsiComment cmt = comments.get(j);
        if (isLineComment(commenter, cmt, doc) && doc.getLineNumber(cmt.getTextRange().getStartOffset()) == eline + 1) {
          lastComment = cmt;
          eline = doc.getLineNumber(cmt.getTextRange().getEndOffset());
        }
        else {
          break;
        }
      }
    }
    return new CommentRange(firstComment, lastComment);
  }

  private static boolean isLineComment(Commenter commenter, PsiComment comment, Document doc) {
    final String lineCommentPrefix = commenter.getLineCommentPrefix();
    if (lineCommentPrefix != null) {
      return comment.getText().startsWith(lineCommentPrefix);
    }
    final TextRange textRange = comment.getTextRange();
    return doc.getLineNumber(textRange.getStartOffset()) == doc.getLineNumber(textRange.getEndOffset());
  }

  protected PsiFile getFile() {
    return file;
  }

  protected LanguageOptions getLanguageOptions() {
    return langOpts;
  }

  protected void addAction(CommentAction action) {
    actions.add(action);
  }

  protected PsiElement getPreviousSibling(PsiElement element) {
    return element == null ? null : element.getPrevSibling();
  }

  protected PsiElement getNextSibling(PsiElement element) {
    return element == null ? null : element.getNextSibling();
  }

  protected void processActions(final boolean allowReplacement) throws IncorrectOperationException {
    new WriteCommandAction.Simple(file.getProject(), "Update copyright") {
      @Override
      protected void run() throws Throwable {
        Document doc = FileDocumentManager.getInstance().getDocument(getRoot());
        if (doc != null) {
          PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(doc);
          for (CommentAction action : actions) {
            int start = action.getStart();
            int end = action.getEnd();
  
            switch (action.getType()) {
              case CommentAction.ACTION_INSERT:
                String comment = getCommentText(action.getPrefix(), action.getSuffix());
                if (!comment.isEmpty()) {
                  doc.insertString(start, comment);
                }
                break;
              case CommentAction.ACTION_REPLACE:
                if (allowReplacement) doc.replaceString(start, end, getCommentText("", ""));
                break;
              case CommentAction.ACTION_DELETE:
                if (allowReplacement) doc.deleteString(start, end);
                break;
            }
          }
          PsiDocumentManager.getInstance(getProject()).commitDocument(doc);
        }
      }
    }.execute();
  }

  public boolean hasUpdates() {
    return !actions.isEmpty();
  }

  private static class CommentRange {
    public CommentRange(@NotNull PsiElement first, @NotNull PsiElement last) {
      this.first = first;
      this.last = last;
      LOG.assertTrue(first.getContainingFile() == last.getContainingFile());
    }

    public PsiElement getFirst() {
      return first;
    }

    public PsiElement getLast() {
      return last;
    }

    private final PsiElement first;
    private final PsiElement last;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CommentRange that = (CommentRange)o;

      if (first != null ? !first.equals(that.first) : that.first != null) return false;
      if (last != null ? !last.equals(that.last) : that.last != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = first != null ? first.hashCode() : 0;
      result = 31 * result + (last != null ? last.hashCode() : 0);
      return result;
    }
  }

  protected static class CommentAction implements Comparable<CommentAction> {
    public static final int ACTION_INSERT = 1;
    public static final int ACTION_REPLACE = 2;
    public static final int ACTION_DELETE = 3;

    public CommentAction(int pos, String prefix, String suffix) {
      type = ACTION_INSERT;
      start = pos;
      end = pos;
      this.prefix = prefix;
      this.suffix = suffix;
    }

    public CommentAction(int type, int start, int end) {
      this.type = type;
      this.start = start;
      this.end = end;
    }

    public int getType() {
      return type;
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }

    public String getPrefix() {
      return prefix;
    }

    public String getSuffix() {
      return suffix;
    }

    @Override
    public int compareTo(@NotNull CommentAction object) {
      int s = object.getStart();
      int diff = s - start;
      if (diff == 0) {
        diff = type == ACTION_INSERT ? 1 : -1;
      }

      return diff;
    }

    private final int type;
    private final int start;
    private final int end;
    private String prefix = null;
    private String suffix = null;
  }

  private final PsiFile file;
  private final LanguageOptions langOpts;
  private final TreeSet<CommentAction> actions = new TreeSet<>();

  private static final Logger logger = Logger.getInstance(UpdatePsiFileCopyright.class.getName());
}
