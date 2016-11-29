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
package com.intellij.openapi.diff.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.diff.impl.fragments.Fragment;
import com.intellij.openapi.diff.impl.fragments.FragmentList;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class MergeOperations {
  private final DiffPanelImpl myDiffPanel;
  private final FragmentSide mySide;
  private static final List<Operation> NO_OPERATIONS = ContainerUtil.emptyList();
  private static final Condition<Fragment> NOT_EQUAL_FRAGMENT = fragment -> fragment.getType() != null;

  public MergeOperations(DiffPanelImpl diffPanel, FragmentSide side) {
    myDiffPanel = diffPanel;
    mySide = side;
  }

  @NotNull
  public List<Operation> getOperations() {
    Fragment fragment = getCurrentFragment();
    if (fragment == null) return NO_OPERATIONS;
    ArrayList<Operation> operations = new ArrayList<>(3);
    TextRange range = fragment.getRange(mySide);
    if (range.getLength() > 0) {
      if (isWritable(mySide)) operations.add(removeOperation(range, getDocument()));
      TextRange otherRange = fragment.getRange(mySide.otherSide());
      boolean otherIsWritable = isWritable(mySide.otherSide());
      if (otherIsWritable) operations.add(insertOperation(range, otherRange.getEndOffset(), getDocument(), getOtherDocument(), mySide));
      if (otherRange.getLength() > 0 && otherIsWritable) operations.add(replaceOperation(range, otherRange, getDocument(), getOtherDocument(), mySide));
    }
    return operations;
  }

  private boolean isWritable(FragmentSide side) {
    Editor editor = myDiffPanel.getEditor(side);
    return !editor.isViewer() && canMakeWritable(editor.getDocument());
  }

  private static boolean canMakeWritable(final Document document) {
    if (document.isWritable()) {
      return true;
    }
    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && file.isInLocalFileSystem()) {
      return true;
    }
    return false;
  }

  public void selectSuggestion() {
    Fragment fragment = getCurrentFragment();
    if (fragment == null) return;
    setSelection(fragment, mySide);
    setSelection(fragment, mySide.otherSide());
  }

  private void setSelection(Fragment fragment, FragmentSide side) {
    TextRange range = fragment.getRange(side);
    if (range.getLength() > 0)
    myDiffPanel.getEditor(side).getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
  }

  private static Operation replaceOperation(TextRange range, TextRange otherRange, Document document, Document otherDocument, FragmentSide base) {
    return new Operation(DiffBundle.message("merge.editor.replace.operation.name"),
                         base == FragmentSide.SIDE1 ? AllIcons.Diff.ArrowRight : AllIcons.Diff.Arrow,
                         otherDocument,
                         replaceModification(range, document, otherRange, otherDocument));
  }

  @Nullable
  public static Operation mostSensible(Document document, Document otherDocument, TextRange range, TextRange otherRange, FragmentSide base) {
    if (!canMakeWritable(document) && !canMakeWritable(otherDocument)) return null;
    if (range.getLength() != 0) {
      if (canMakeWritable(otherDocument))
        return otherRange.getLength() != 0 ?
               replaceOperation(range, otherRange, document, otherDocument, base) :
               insertOperation(range, otherRange.getEndOffset(), document, otherDocument, base);
      else return otherRange.getLength() == 0 ? removeOperation(range, document) : null;
    }
    return null;
  }

  private static Runnable replaceModification(TextRange range, Document document,
                                       final TextRange otherRange, final Document otherDocument) {
    final String replacement = getSubstring(document, range);
    return () -> otherDocument.replaceString(otherRange.getStartOffset(), otherRange.getEndOffset(), replacement);
  }

  private static Operation insertOperation(TextRange range, int offset, Document document, Document otherDocument, FragmentSide base) {
    return new Operation(DiffBundle.message("merge.editor.insert.operation.name"),
                         base == FragmentSide.SIDE1 ? AllIcons.Diff.ArrowRightDown : AllIcons.Diff.ArrowLeftDown,
                         otherDocument,
                         insertModification(range, document, offset, otherDocument));
  }

  private static Runnable insertModification(TextRange range, Document document,
                                      final int offset, final Document otherDocument) {
    final String insertion = getSubstring(document, range);
    return () -> otherDocument.insertString(offset, insertion);
  }

  private static String getSubstring(Document document, TextRange range) {
    return document.getText(range);
  }

  private Document getOtherDocument() {
    return myDiffPanel.getEditor(mySide.otherSide()).getDocument();
  }

  private static Operation removeOperation(TextRange range, Document document) {
    return new Operation(DiffBundle.message("merge.editor.remove.operation.name"),
                         AllIcons.Diff.Remove,
                         document,
                         removeModification(range, document));
  }

  private static Runnable removeModification(final TextRange range, final Document document) {
    return () -> document.deleteString(range.getStartOffset(), range.getEndOffset());
  }

  private Document getDocument() {
    return myDiffPanel.getEditor(mySide).getDocument();
  }

  public Fragment getCurrentFragment() {
    FragmentList fragments = myDiffPanel.getFragments();
    int caretPosition = myDiffPanel.getEditor(mySide).getCaretModel().getOffset();
    return fragments.getFragmentAt(caretPosition, mySide, NOT_EQUAL_FRAGMENT);
  }

  public static class Operation {
    private final String myName;
    private final Document myDocument;
    private final Runnable myModification;
    private final Icon myGutterIcon;
    private boolean myPerformed = false;

    public Operation(String name, Icon icon, final Document document, Runnable modification) {
      myName = name;
      myGutterIcon = icon;
      myDocument = document;
      myModification = modification;
    }

    public Icon getGutterIcon() {
      return myGutterIcon;
    }

    public String getName() {
      return myName;
    }

    public void perform(final Project project) {
      if (myPerformed) return;
      myPerformed = true;
      if (!myDocument.isWritable()) {
        final VirtualFile file = FileDocumentManager.getInstance().getFile(myDocument);
        final ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file);
        if (status.hasReadonlyFiles()) return;
      }
      ApplicationManager.getApplication().runWriteAction(() -> CommandProcessor.getInstance().executeCommand(project, myModification, getName(), null));
    }
  }
}
