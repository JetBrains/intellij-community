// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.ex.FileLookup.Finder;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ApiStatus.Experimental
public final class FileTextFieldUtil {
  private static final Logger LOG = Logger.getInstance(FileTextFieldUtil.class);

  public static void processCompletion(FileTextFieldImpl.CompletionResult result,
                                       @NotNull Finder finder,
                                       @NotNull FileLookup.LookupFilter filter,
                                       @NotNull String fileSpitRegExp,
                                       @NotNull Map<String, String> macroMap) {
    result.myToComplete = new ArrayList<>();
    result.mySiblings = new ArrayList<>();
    result.myKidsAfterSeparator = new ArrayList<>();
    String typed = result.myCompletionBase;

    if (typed == null) return;

    FileTextFieldImpl.addMacroPaths(result, typed, finder, macroMap);

    String typedText = finder.normalize(typed);


    result.current = getClosestParent(typed, finder, fileSpitRegExp);
    result.myClosestParent = result.current;

    if (result.current != null) {
      result.currentParentMatch = SystemInfo.isFileSystemCaseSensitive
                                  ? typedText.equals(result.current.getAbsolutePath())
                                  : typedText.equalsIgnoreCase(result.current.getAbsolutePath());

      result.closedPath = typed.endsWith(finder.getSeparator()) && typedText.length() > finder.getSeparator().length();
      String currentParentText = result.current.getAbsolutePath();

      if (!StringUtil.toUpperCase(typedText).startsWith(StringUtil.toUpperCase(currentParentText))) return;

      String prefix = typedText.substring(currentParentText.length());
      if (prefix.startsWith(finder.getSeparator())) {
        prefix = prefix.substring(finder.getSeparator().length());
      }
      else if (typed.endsWith(finder.getSeparator())) {
        prefix = "";
      }

      result.effectivePrefix = prefix;

      result.currentGrandparent = result.current.getParent();
      if (result.currentGrandparent != null && result.currentParentMatch && !result.closedPath) {
        String currentGrandparentText = result.currentGrandparent.getAbsolutePath();
        if (StringUtil.startsWithConcatenation(typedText, currentGrandparentText, finder.getSeparator())) {
          result.grandparentPrefix = currentParentText.substring(currentGrandparentText.length() + finder.getSeparator().length());
        }
      }
    }
    else {
      result.effectivePrefix = typedText;
    }

    ReadAction.run(new ThrowableRunnable<>() {
      @Override
      public void run() {
        if (result.current != null) {
          result.myToComplete.addAll(getMatchingChildren(result.effectivePrefix, result.current));

          if (result.currentParentMatch && !result.closedPath && !typed.isEmpty()) {
            result.myKidsAfterSeparator.addAll(result.myToComplete);
          }

          if (result.grandparentPrefix != null) {
            List<FileLookup.LookupFile> siblings = getMatchingChildren(result.grandparentPrefix, result.currentGrandparent);
            result.myToComplete.addAll(0, siblings);
            result.mySiblings.addAll(siblings);
          }
        }

        FileLookup.LookupFile toPreselect = result.myPreselected;

        if (toPreselect == null || !result.myToComplete.contains(toPreselect)) {
          boolean toPreselectFixed = false;
          if (result.effectivePrefix.length() > 0) {
            for (FileLookup.LookupFile each : result.myToComplete) {
              String eachName = StringUtil.toUpperCase(each.getName());
              if (!eachName.startsWith(result.effectivePrefix)) continue;
              toPreselect = each;
              toPreselectFixed = true;
              break;
            }

            if (!toPreselectFixed) {
              toPreselect = null;
            }
          }
          else {
            toPreselect = null;
          }

          if (toPreselect == null) {
            if (result.myToComplete.size() == 1) {
              toPreselect = result.myToComplete.get(0);
            }
            else if (result.effectivePrefix.length() == 0) {
              if (result.mySiblings.size() > 0) {
                toPreselect = result.mySiblings.get(0);
              }
            }

            if (toPreselect == null && !result.myToComplete.contains(null) && result.myToComplete.size() > 0) {
              toPreselect = result.myToComplete.get(0);
            }
          }
        }

        if (result.currentParentMatch && result.mySiblings.size() > 0) {
          toPreselect = null;
        }

        result.myPreselected = toPreselect;
      }

      private List<FileLookup.LookupFile> getMatchingChildren(String prefix, FileLookup.LookupFile parent) {
        MinusculeMatcher matcher = createMatcher(prefix);
        return parent.getChildren(new FileLookup.LookupFilter() {
          @Override
          public boolean isAccepted(FileLookup.LookupFile file) {
            return !file.equals(result.current) && filter.isAccepted(file) && matcher.matches(file.getName());
          }
        });
      }
    });
  }

  static MinusculeMatcher createMatcher(String prefix) {
    return NameUtil.buildMatcher("*" + prefix, NameUtil.MatchingCaseSensitivity.NONE);
  }

  private static @Nullable FileLookup.LookupFile getClosestParent(String typed, Finder finder, String fileSpitRegExp) {
    if (typed == null) return null;
    FileLookup.LookupFile lastFound = finder.find(typed);
    if (lastFound == null) return null;
    if (typed.isEmpty()) return lastFound;
    if (lastFound.exists()) {
      if (typed.charAt(typed.length() - 1) != File.separatorChar) return lastFound.getParent();
      return lastFound;
    }

    String[] splits = finder.normalize(typed).split(fileSpitRegExp);
    StringBuilder fullPath = new StringBuilder();
    for (int i = 0; i < splits.length; i++) {
      String each = splits[i];
      fullPath.append(each);
      if (i < splits.length - 1) {
        fullPath.append(finder.getSeparator());
      }
      FileLookup.LookupFile file = finder.find(fullPath.toString());
      if (file == null || !file.exists()) return lastFound;
      lastFound = file;
    }

    return lastFound;
  }

  public static @NlsSafe @NotNull String getLookupString(@NotNull FileLookup.LookupFile file,
                                                         @NotNull Finder finder,
                                                         @Nullable FileTextFieldImpl.CompletionResult result) {
    String macro = file.getMacro();
    if (macro != null) return macro;
    String prefix = result != null && result.myKidsAfterSeparator.contains(file) ? finder.getSeparator() : "";
    return prefix + file.getName();
  }

  public interface DocumentOwner {
    String getText(int offset, int length) throws BadLocationException;

    void remove(int offs, int len) throws BadLocationException;

    void insertString(int offset, String str) throws BadLocationException;

    int getLength();

    void removeSelection();

    void setCaretPosition(int position);

    int getCaretPosition();

    void setText(@NotNull String text);

    void setTextToFile(@NotNull FileLookup.LookupFile file);
  }

  public static class TextFieldDocumentOwner implements DocumentOwner {
    private final JTextField myField;
    private final Document myDocument;
    private final @NotNull Consumer<? super FileLookup.LookupFile> mySetText;

    public TextFieldDocumentOwner(@NotNull JTextField field, @NotNull Consumer<? super FileLookup.LookupFile> setText) {
      myField = field;
      myDocument = field.getDocument();
      mySetText = setText;
    }

    @Override
    public String getText(int offset, int length) throws BadLocationException {
      return myDocument.getText(offset, length);
    }

    @Override
    public void remove(int offset, int length) throws BadLocationException {
      myDocument.remove(offset, length);
    }

    @Override
    public void insertString(int offset, String str) throws BadLocationException {
      myDocument.insertString(offset, str, myDocument.getDefaultRootElement().getAttributes());
    }

    @Override
    public int getLength() {
      return myDocument.getLength();
    }

    @Override
    public void removeSelection() {
      myField.setSelectionStart(0);
      myField.setSelectionEnd(0);
    }

    @Override
    public void setCaretPosition(int position) {
      myField.setCaretPosition(position);
    }

    @Override
    public int getCaretPosition() {
      return myField.getCaretPosition();
    }

    @Override
    public void setText(@NotNull String text) {
      myField.setText(text);
    }

    @Override
    public void setTextToFile(FileLookup.@NotNull LookupFile file) {
      mySetText.accept(file);
    }
  }

  /**
   * Replace the path component under the caret with the file selected from the completion list.
   *
   * @param file     the selected file.
   * @param caretPos
   * @param start    the start offset of the path component under the caret.
   * @param end      the end offset of the path component under the caret.
   * @throws BadLocationException
   */
  private static void replacePathComponent(@NotNull FileLookup.LookupFile file,
                                           @NotNull DocumentOwner doc,
                                           @NotNull Finder finder,
                                           int caretPos,
                                           int start,
                                           int end) throws BadLocationException {

    doc.removeSelection();

    String name = file.getName();
    boolean toRemoveExistingName;

    if (caretPos >= start) {
      String prefix = doc.getText(start, caretPos - start);
      if (prefix.length() == 0) {
        prefix = doc.getText(start, end - start);
      }
      if (SystemInfo.isFileSystemCaseSensitive) {
        toRemoveExistingName = name.startsWith(prefix) && prefix.length() > 0;
      }
      else {
        toRemoveExistingName = StringUtil.toUpperCase(name).startsWith(StringUtil.toUpperCase(prefix)) && prefix.length() > 0;
      }
    }
    else {
      toRemoveExistingName = true;
    }

    int newPos;
    if (toRemoveExistingName) {
      doc.remove(start, end - start);
      doc.insertString(start, name);
      newPos = start + name.length();
    }
    else {
      doc.insertString(caretPos, name);
      newPos = caretPos + name.length();
    }

    if (file.isDirectory()) {
      if (!finder.getSeparator().equals(doc.getText(newPos, 1))) {
        doc.insertString(newPos, finder.getSeparator());
        newPos++;
      }
    }

    if (newPos < doc.getLength()) {
      if (finder.getSeparator().equals(doc.getText(newPos, 1))) {
        newPos++;
      }
    }
    doc.setCaretPosition(newPos);
  }

  public static void processChosenFromCompletion(FileLookup.LookupFile file,
                                                 DocumentOwner doc,
                                                 Finder finder,
                                                 boolean nameOnly) {
    if (file == null) return;

    if (nameOnly) {
      try {
        int caretPos = doc.getCaretPosition();
        if (finder.getSeparator().equals(doc.getText(caretPos, 1))) {
          for (; caretPos < doc.getLength(); caretPos++) {
            String eachChar = doc.getText(caretPos, 1);
            if (!finder.getSeparator().equals(eachChar)) break;
          }
        }

        int start = caretPos > 0 ? caretPos - 1 : caretPos;
        while (start >= 0) {
          String each = doc.getText(start, 1);
          if (finder.getSeparator().equals(each)) {
            start++;
            break;
          }
          start--;
        }

        int end = Math.max(start, caretPos);
        while (end <= doc.getLength()) {
          String each = doc.getText(end, 1);
          if (finder.getSeparator().equals(each)) {
            break;
          }
          end++;
        }

        if (end > doc.getLength()) {
          end = doc.getLength();
        }

        if (start > end || start < 0 || end > doc.getLength()) {
          doc.setText(file.getAbsolutePath());
        }
        else {
          replacePathComponent(file, doc, finder, caretPos, start, end);
        }
      }
      catch (BadLocationException e) {
        LOG.error(e);
      }
    }
    else {
      doc.setTextToFile(file);
    }
  }

  public static void setTextToFile(@NotNull FileLookup.LookupFile file, Finder finder, @NotNull DocumentOwner doc) {
    String text = file.getAbsolutePath();
    if (file.isDirectory() && !text.endsWith(finder.getSeparator())) {
      text += finder.getSeparator();
    }
    doc.setText(text);
  }
}
