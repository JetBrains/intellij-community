// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.modifiers;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.FinishMarkAction;
import com.intellij.openapi.command.impl.StartMarkAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeModifierIntention extends BaseElementAtCaretIntentionAction {
  private static final List<AccessModifier> ALL_MODIFIERS = ContainerUtil.immutableList(AccessModifier.values());
  private static final List<AccessModifier> PUBLIC_PRIVATE = ContainerUtil.immutableList(AccessModifier.PUBLIC, AccessModifier.PRIVATE);
  private static final List<AccessModifier> PUBLIC_PACKAGE =
    ContainerUtil.immutableList(AccessModifier.PUBLIC, AccessModifier.PACKAGE_LOCAL);

  private AccessModifier myTarget;

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiMember member = findMember(element);
    if (!(member instanceof PsiNameIdentifierOwner)) return false;
    PsiElement identifier = ((PsiNameIdentifierOwner)member).getNameIdentifier();
    if (identifier == null || identifier.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) return false;
    List<AccessModifier> modifiers = new ArrayList<>(getAvailableModifiers(member));
    if (modifiers.isEmpty()) return false;
    modifiers.removeIf(mod -> mod.hasModifier(member));
    AccessModifier target = null;
    if (modifiers.isEmpty()) return false;
    if (modifiers.size() == 1) {
      target = modifiers.get(0);
      setText("Make " + getModifierPresentation(target));
    }
    else {
      setText(getFamilyName());
    }
    myTarget = target;
    return true;
  }

  private static PsiMember findMember(@NotNull PsiElement element) {
    while (true) {
      PsiMember member =
        PsiTreeUtil.getParentOfType(element, PsiMember.class, false, PsiCodeBlock.class, PsiStatement.class, PsiExpression.class);
      if (!(member instanceof PsiTypeParameter)) {
        return member;
      }
      element = member.getParent();
    }
  }

  @NotNull
  private static String getModifierPresentation(AccessModifier modifier) {
    return modifier.equals(AccessModifier.PACKAGE_LOCAL) ? "package-private" : "'" + modifier + "'";
  }

  @NotNull
  private static List<AccessModifier> getAvailableModifiers(PsiMember member) {
    if (member == null) return Collections.emptyList();
    PsiClass containingClass = member.getContainingClass();
    if (member instanceof PsiField) {
      if (member instanceof PsiEnumConstant || containingClass == null || containingClass.isInterface()) return Collections.emptyList();
      return ALL_MODIFIERS;
    }
    if (member instanceof PsiMethod) {
      if (containingClass == null || containingClass.isEnum() && ((PsiMethod)member).isConstructor()) return Collections.emptyList();
      if (containingClass.isInterface()) {
        if (PsiUtil.isLanguageLevel9OrHigher(member)) {
          return PUBLIC_PRIVATE;
        }
      }
      return ALL_MODIFIERS;
    }
    if (member instanceof PsiClass) {
      if (PsiUtil.isLocalOrAnonymousClass((PsiClass)member)) return Collections.emptyList();
      if (containingClass == null) return PUBLIC_PACKAGE;
      return ALL_MODIFIERS;
    }
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiMember member = findMember(element);
    if (member == null) return;
    PsiFile file = member.getContainingFile();
    if (file == null) return;
    List<AccessModifier> modifiers = getAvailableModifiers(member);
    if (modifiers.isEmpty()) return;
    AccessModifier target = myTarget;
    if (modifiers.contains(target)) {
      setModifier(member, target);
      return;
    }
    TextRange range = getRange(member);
    Document document = editor.getDocument();
    CharSequence sequence = document.getCharsSequence();
    if (range.getLength() == 0) {
      int pos = range.getStartOffset();
      while (pos < sequence.length() && StringUtil.isWhiteSpace(sequence.charAt(pos))) {
        pos++;
      }
      range = TextRange.from(pos, 0);
    }
    CaretModel model = editor.getCaretModel();
    RangeMarker cursorMarker = document.createRangeMarker(model.getOffset(), model.getOffset());
    model.moveToOffset(range.getStartOffset());
    StartMarkAction markAction;
    try {
      markAction = StartMarkAction.start(editor, project, getFamilyName());
    }
    catch (StartMarkAction.AlreadyStartedException e) {
      Messages.showErrorDialog(project, e.getMessage(), StringUtil.toTitleCase(getFamilyName()));
      return;
    }

    TextAttributes lvAttr = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.LIVE_TEMPLATE_ATTRIBUTES);
    RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(range.getStartOffset(), range.getEndOffset(), HighlighterLayer.LAST + 1, lvAttr,
                           HighlighterTargetArea.EXACT_RANGE);
    highlighter.setGreedyToRight(true);
    highlighter.setGreedyToLeft(true);
    ModifierUpdater updater = new ModifierUpdater(file, document, range, getFamilyName());
    AccessModifier current = ContainerUtil.find(modifiers, m -> m.hasModifier(member));
    SmartPsiElementPointer<PsiMember> memberPointer = SmartPointerManager.createPointer(member);
    JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(modifiers)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setSelectedValue(current, true)
      .setAccessibleName("Change Modifier")
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setFont(editor.getColorsScheme().getFont(EditorFontType.PLAIN))
      .setItemSelectedCallback(updater::setModifier)
      .addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          highlighter.dispose();
          model.moveToOffset(cursorMarker.getStartOffset());
          if (!event.isOk()) {
            FinishMarkAction.finish(project, editor, markAction);
            updater.undoChange(true);
          }
        }
      })
      .setNamerForFiltering(AccessModifier::toString)
      .setItemChosenCallback(t -> {
        updater.undoChange(false);
        PsiDocumentManager.getInstance(project).commitDocument(document);
        updater.setModifier(t);
        // do not commit document now: checkForConflicts should have original content
        // while the editor should display the updated content to prevent flicker
        PsiMember m = memberPointer.getElement();
        if (m == null) return;
        PsiModifierList modifierList = m.getModifierList();
        if (modifierList == null) return;
        final MultiMap<PsiElement, String> conflicts = checkForConflicts(m, t);
        if (conflicts == null) {
          //canceled by user
          FinishMarkAction.finish(project, editor, markAction);
          updater.undoChange(true);
          return;
        }
        if (!conflicts.isEmpty()) {
          FinishMarkAction.finish(project, editor, markAction);
          updater.undoChange(true);
          PsiDocumentManager.getInstance(project).commitDocument(document);
          processWithConflicts(modifierList, t, conflicts);
        } else {
          updater.undoChange(false);
          PsiDocumentManager.getInstance(project).commitDocument(document);
          changeModifier(modifierList, t, false);
          FinishMarkAction.finish(project, editor, markAction);
        }
      })
      .createPopup();
    popup.showInBestPositionFor(editor);
  }

  private static class ModifierUpdater {
    private final Document myDocument;
    private final boolean myExtendLeft, myExtendRight;
    private final String myOriginalText;
    private final RangeMarker myMarker;
    private final String myActionName;
    private final PsiFile myFile;

    ModifierUpdater(@NotNull PsiFile file, @NotNull Document document, @NotNull TextRange range, @NotNull String actionName) {
      myDocument = document;
      myFile = file;
      myActionName = actionName;
      CharSequence sequence = document.getCharsSequence();
      myExtendLeft = range.getStartOffset() > 0 && !StringUtil.isWhiteSpace(sequence.charAt(range.getStartOffset() - 1));
      myExtendRight = range.getEndOffset() < sequence.length() && !StringUtil.isWhiteSpace(sequence.charAt(range.getEndOffset()));
      myOriginalText = sequence.subSequence(range.getStartOffset(), range.getEndOffset()).toString();
      myMarker = document.createRangeMarker(range);
      myMarker.setGreedyToRight(true);
      myMarker.setGreedyToLeft(true);
    }

    void undoChange(boolean viaUndoManager) {
      Project project = myFile.getProject();
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      FileEditor fileEditor = fileEditorManager.getSelectedEditor(myFile.getVirtualFile());
      UndoManager manager = UndoManager.getInstance(project);
      if (viaUndoManager && manager.isUndoAvailable(fileEditor)) {
        manager.undo(fileEditor);
      }
      else {
        WriteCommandAction.writeCommandAction(project, myFile)
          .withName(myActionName)
          .run(() -> myDocument.replaceString(myMarker.getStartOffset(), myMarker.getEndOffset(), myOriginalText));
      }
    }

    void setModifier(@Nullable AccessModifier target) {
      if (target == null) return;
      String updatedText;
      if (target == AccessModifier.PACKAGE_LOCAL) {
        updatedText = " ";
      }
      else {
        updatedText = myExtendLeft ? myExtendRight ? " " + target + " " : " " + target
                                   : myExtendRight ? target + " " : target.toString();
      }
      WriteCommandAction.writeCommandAction(myFile.getProject(), myFile)
        .withName(myActionName)
        .run(() -> myDocument.replaceString(myMarker.getStartOffset(), myMarker.getEndOffset(), updatedText));
    }
  }

  private static TextRange getRange(PsiMember member) {
    PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) {
      return TextRange.from(member.getTextRange().getStartOffset(), 0);
    }
    PsiElement anchor = getAnchorKeyword(modifierList);
    if (anchor != null) {
      return anchor.getTextRange();
    }
    anchor = PsiTreeUtil.getChildOfType(modifierList, PsiKeyword.class);
    if (anchor != null) {
      return TextRange.from(anchor.getTextRange().getStartOffset(), 0);
    }
    return TextRange.from(modifierList.getTextRange().getEndOffset(), 0);
  }

  @Nullable
  private static PsiKeyword getAnchorKeyword(PsiModifierList modifierList) {
    for (PsiElement child : modifierList.getChildren()) {
      if (ALL_MODIFIERS.contains(AccessModifier.fromKeyword(ObjectUtils.tryCast(child, PsiKeyword.class)))) {
        return (PsiKeyword)child;
      }
    }
    return null;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return IntentionPowerPackBundle.message("change.modifier.intention.name");
  }

  private static void setModifier(PsiMember member, AccessModifier modifier) {
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null) return;
    final MultiMap<PsiElement, String> conflicts = checkForConflicts(member, modifier);
    if (conflicts == null) {
      //canceled by user
      return;
    }
    processWithConflicts(modifierList, modifier, conflicts);
  }

  private static void processWithConflicts(@NotNull PsiModifierList modifierList,
                                           @NotNull AccessModifier modifier,
                                           @NotNull MultiMap<PsiElement, String> conflicts) {
    boolean shouldProcess;
    if (conflicts.isEmpty()) {
      shouldProcess = true;
    }
    else if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      }
      shouldProcess = true;
    }
    else {
      ConflictsDialog dialog =
        new ConflictsDialog(modifierList.getProject(), conflicts, () -> changeModifier(modifierList, modifier, true));
      shouldProcess = dialog.showAndGet();
    }
    if (shouldProcess) {
      changeModifier(modifierList, modifier, !conflicts.isEmpty());
    }
  }

  private static void changeModifier(PsiModifierList modifierList, AccessModifier modifier, boolean hasConflicts) {
    PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiMethod && hasConflicts) {
      PsiMethod method = (PsiMethod)parent;
      //no myPrepareSuccessfulSwingThreadCallback means that the conflicts when any, won't be shown again
      new ChangeSignatureProcessor(parent.getProject(),
                                   method,
                                   false,
                                   modifier.toPsiModifier(),
                                   method.getName(),
                                   method.getReturnType(),
                                   ParameterInfoImpl.fromMethod(method),
                                   JavaThrownExceptionInfo.extractExceptions(method))
        .run();
      return;
    }
    PsiFile file = modifierList.getContainingFile();
    WriteCommandAction.writeCommandAction(file.getProject(), file)
      .withName(IntentionPowerPackBundle.message("change.modifier.intention.name"))
      .run(() -> {
      modifierList.setModifierProperty(modifier.toPsiModifier(), true);
      if (modifier != AccessModifier.PACKAGE_LOCAL) {
        final Project project = modifierList.getProject();
        final PsiElement whitespace = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(" ");
        final PsiElement sibling = modifierList.getNextSibling();
        if (sibling instanceof PsiWhiteSpace) {
          sibling.replace(whitespace);
          CodeStyleManager.getInstance(project).reformatRange(parent, modifierList.getTextOffset(),
                                                              modifierList.getNextSibling().getTextOffset());
        }
      }
    });
  }

  @Nullable
  private static MultiMap<PsiElement, String> checkForConflicts(@NotNull PsiMember member, AccessModifier modifier) {
    if (member instanceof PsiClass && modifier == AccessModifier.PUBLIC) {
      final PsiClass aClass = (PsiClass)member;
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiJavaFile)) {
        return MultiMap.emptyInstance();
      }
      final PsiJavaFile javaFile = (PsiJavaFile)parent;
      final String name = FileUtilRt.getNameWithoutExtension(javaFile.getName());
      final String className = aClass.getName();
      if (name.equals(className)) {
        return MultiMap.emptyInstance();
      }
      final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
      conflicts.putValue(aClass, IntentionPowerPackBundle.message(
        "0.is.declared.in.1.but.when.public.should.be.declared.in.a.file.named.2",
        RefactoringUIUtil.getDescription(aClass, false),
        RefactoringUIUtil.getDescription(javaFile, false),
        CommonRefactoringUtil.htmlEmphasize(className + ".java")));
      return conflicts;
    }
    final PsiModifierList modifierList = member.getModifierList();
    if (modifierList == null || modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
      return MultiMap.emptyInstance();
    }
    PsiModifierList copy = (PsiModifierList)modifierList.copy();
    copy.setModifierProperty(modifier.toPsiModifier(), true);
    SearchScope useScope = member.getUseScope();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(() -> {
      if (member instanceof PsiMethod) {
        JavaChangeSignatureUsageProcessor.ConflictSearcher.searchForHierarchyConflicts((PsiMethod)member, conflicts, modifier.toPsiModifier());
      }

      final Query<PsiReference> search = ReferencesSearch.search(member, useScope);
      search.forEach(reference -> {
        final PsiElement element = reference.getElement();
        if (JavaResolveUtil.isAccessible(member, member.getContainingClass(), copy, element, null, null)) {
          return true;
        }
        final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiField.class, PsiClass.class, PsiFile.class);
        if (context == null) {
          return true;
        }
        conflicts.putValue(element, RefactoringBundle.message("0.with.1.visibility.is.not.accessible.from.2",
                                                              RefactoringUIUtil.getDescription(member, false),
                                                              PsiBundle.visibilityPresentation(modifier.toPsiModifier()),
                                                              RefactoringUIUtil.getDescription(context, true)));
        return true;
      });
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, member.getProject())) {
      return null;
    }
    return conflicts;
  }
}
