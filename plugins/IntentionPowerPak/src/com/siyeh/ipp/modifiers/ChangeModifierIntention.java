// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.modifiers;

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.AccessModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.suggested.SuggestedRefactoringProvider;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ChangeModifierIntention extends BaseElementAtCaretIntentionAction {

  private final boolean myErrorFix;
  private AccessModifier myTarget;

  // Necessary to register an extension
  public ChangeModifierIntention() {
    this(false);
  }

  public ChangeModifierIntention(boolean errorFix) {
    myErrorFix = errorFix;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return false;
    PsiMember member = findMember(element);
    if (!(member instanceof PsiNameIdentifierOwner)) return false;
    PsiElement identifier = ((PsiNameIdentifierOwner)member).getNameIdentifier();
    if (identifier == null || identifier.getTextRange().getEndOffset() <= element.getTextRange().getStartOffset()) return false;
    List<AccessModifier> modifiers = new ArrayList<>(AccessModifier.getAvailableModifiers(member));
    if (modifiers.isEmpty()) return false;
    if (!myErrorFix && !ContainerUtil.exists(modifiers, mod -> mod.hasModifier(member))) return false;
    modifiers.removeIf(mod -> mod.hasModifier(member));
    AccessModifier target = null;
    if (modifiers.isEmpty()) return false;
    if (modifiers.size() == 1) {
      target = modifiers.get(0);
      setText(IntentionPowerPackBundle.message("change.modifier.text", identifier.getText(), target));
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
    List<AccessModifier> modifiers = AccessModifier.getAvailableModifiers(member);
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

    RangeHighlighter highlighter = editor.getMarkupModel()
      .addRangeHighlighter(EditorColors.LIVE_TEMPLATE_ATTRIBUTES, range.getStartOffset(), range.getEndOffset(), HighlighterLayer.LAST + 1,
                           HighlighterTargetArea.EXACT_RANGE);
    highlighter.setGreedyToRight(true);
    highlighter.setGreedyToLeft(true);
    ModifierUpdater updater = new ModifierUpdater(file, document, range, getFamilyName());
    AccessModifier current = ContainerUtil.find(modifiers, m -> m.hasModifier(member));
    SmartPsiElementPointer<PsiMember> memberPointer = SmartPointerManager.createPointer(member);
    JBPopup popup = JBPopupFactory.getInstance().createPopupChooserBuilder(modifiers)
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setSelectedValue(current, true)
      .setAccessibleName(JavaBundle.message("accessible.name.change.modifier"))
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
          FinishMarkAction.finish(project, editor, markAction);
          if (!event.isOk()) {
            updater.undoChange();
          }
        }
      })
      .setNamerForFiltering(AccessModifier::toString)
      .setItemChosenCallback(t -> {
        if (editor instanceof EditorImpl) {
          ((EditorImpl)editor).startDumb();
        }
        MultiMap<PsiElement, String> conflicts;
        PsiModifierList modifierList;
        try {
          updater.undoChange();
          PsiDocumentManager.getInstance(project).commitDocument(document);
          if (t == current) return;
          PsiMember m = memberPointer.getElement();
          if (m == null) return;
          modifierList = m.getModifierList();
          if (modifierList == null) return;
          conflicts = checkForConflicts(m, t);
        }
        finally {
          if (editor instanceof EditorImpl) {
            ((EditorImpl)editor).stopDumbLater();
          }
        }
        if (conflicts == null) {
          return;
        }
        if (!conflicts.isEmpty()) {
          processWithConflicts(modifierList, t, conflicts);
        } else {
          changeModifier(modifierList, t, false);
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
    private final @NlsContexts.Command String myActionName;
    private final PsiFile myFile;

    ModifierUpdater(@NotNull PsiFile file, @NotNull Document document, @NotNull TextRange range, @NotNull @NlsContexts.Command String actionName) {
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

    void undoChange() {
      Project project = myFile.getProject();
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      FileEditor fileEditor = fileEditorManager.getSelectedEditor(myFile.getVirtualFile());
      UndoManager manager = UndoManager.getInstance(project);
      if (manager.isUndoAvailable(fileEditor)) {
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
    for (PsiElement child = modifierList.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (AccessModifier.ALL_MODIFIERS.contains(AccessModifier.fromKeyword(ObjectUtils.tryCast(child, PsiKeyword.class)))) {
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
    Project project = modifierList.getProject();
    PsiElement parent = modifierList.getParent();
    if (parent instanceof PsiMethod && hasConflicts) {
      PsiMethod method = (PsiMethod)parent;
      //no myPrepareSuccessfulSwingThreadCallback means that the conflicts when any, won't be shown again
      var provider = JavaSpecialRefactoringProvider.getInstance();
      var csp = provider.getChangeSignatureProcessor(project,
                                                                 method,
                                                                 false,
                                                                 modifier.toPsiModifier(),
                                                                 method.getName(),
                                                                 method.getReturnType(),
                                                                 ParameterInfoImpl.fromMethod(method),
                                                                 JavaThrownExceptionInfo.extractExceptions(method)
                                                                 );
      csp.run();
      return;
    }
    PsiFile file = modifierList.getContainingFile();
    WriteCommandAction.writeCommandAction(project, file)
      .withName(IntentionPowerPackBundle.message("change.modifier.intention.name"))
      .run(() -> {
        VisibilityUtil.setVisibility(modifierList, modifier.toPsiModifier());
        if (modifier != AccessModifier.PACKAGE_LOCAL) {
          final PsiElement whitespace = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(" ");
          final PsiElement sibling = modifierList.getNextSibling();
          if (sibling instanceof PsiWhiteSpace) {
            sibling.replace(whitespace);
            CodeStyleManager.getInstance(project).reformatRange(parent, modifierList.getTextOffset(),
                                                                modifierList.getNextSibling().getTextOffset());
          }
        }
        SuggestedRefactoringProvider.getInstance(project).reset();
      });
  }

  @Nullable
  private static MultiMap<PsiElement, String> checkForConflicts(@NotNull PsiMember member, AccessModifier modifier) {
    if (member instanceof PsiClass && modifier == AccessModifier.PUBLIC) {
      final PsiClass aClass = (PsiClass)member;
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiJavaFile)) {
        return MultiMap.empty();
      }
      final PsiJavaFile javaFile = (PsiJavaFile)parent;
      final String name = FileUtilRt.getNameWithoutExtension(javaFile.getName());
      final String className = aClass.getName();
      if (name.equals(className)) {
        return MultiMap.empty();
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
      return MultiMap.empty();
    }
    
    SearchScope useScope = member.getUseScope();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ReadAction.run(() -> {
      PsiModifierList copy = (PsiModifierList)modifierList.copy();
      copy.setModifierProperty(modifier.toPsiModifier(), true);

      if (member instanceof PsiMethod) {
        JavaSpecialRefactoringProvider.getInstance().searchForHierarchyConflicts((PsiMethod)member, conflicts, modifier.toPsiModifier());
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
                                                              JavaPsiBundle.visibilityPresentation(modifier.toPsiModifier()),
                                                              RefactoringUIUtil.getDescription(context, true)));
        return true;
      });
    }), RefactoringBundle.message("detecting.possible.conflicts"), true, member.getProject())) {
      return null;
    }
    return conflicts;
  }
}
