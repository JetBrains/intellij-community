package com.intellij.grazie.ide.inspection.auto;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.fus.AcceptanceRateTracker;
import com.intellij.grazie.ide.fus.GrazieProCounterUsagesCollector;
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TreeRuleChecker.TreeProblem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Map;

public final class AutoFix {
  public static void consider(TextContent text, List<TreeProblem> problems) {
    if (GrazieConfig.Companion.get().getAutoFix() && !problems.isEmpty()) {
      PsiFile file = text.getContainingFile();
      Document document = file.getViewProvider().getDocument();
      List<TreeProblem> candidates = ContainerUtil.filter(problems, p -> shouldAutoFix(document, p));
      if (!candidates.isEmpty()) {
        scheduleAutoFix(file, candidates);
      }
    }
  }

  private static void scheduleAutoFix(PsiFile file, List<TreeProblem> problems) {
    var fixes = collectFixes(problems);
    Document document = file.getViewProvider().getDocument();
    long stamp = document.getModificationStamp();
    ApplicationManager.getApplication().invokeLater(() -> {
      if (file.isValid() && stamp == document.getModificationStamp()) {
        applyFixes(file, fixes);
      }
    });
  }

  private static List<IntentionAndQuickFixAction> selectFixes(List<LocalQuickFix> fixes) {
    return StreamEx.of(fixes).select(IntentionAndQuickFixAction.class).toList();
  }

  private static Map<TreeProblem, List<IntentionAndQuickFixAction>> collectFixes(List<TreeProblem> candidates) {
    return StreamEx.of(candidates)
      .mapToEntry(it -> selectFixes(GrazieReplaceTypoQuickFix.getReplacementFixes(it, List.of())))
      .toMap();
  }

  private static void applyFixes(PsiFile file, Map<TreeProblem, List<IntentionAndQuickFixAction>> fixes) {
    var project = file.getProject();
    for (var entry: fixes.entrySet()) {
      var problem = entry.getKey();
      for (var fix: entry.getValue()) {
        var tracker = new AcceptanceRateTracker(entry.getKey());
        WriteCommandAction.runWriteCommandAction(project, commandName(problem), null, () -> {
          var action = new BasicUndoableAction(file.getViewProvider().getVirtualFile()) {
            @Override
            public void undo() {
              GrazieProCounterUsagesCollector.reportAutoFixUndone(tracker);
            }

            @Override
            public void redo() {
              fix.applyFix(project, file, null);
              GrazieProCounterUsagesCollector.reportAutoFixApplied(tracker);
            }
          };
          action.redo();
          UndoManager.getInstance(project).undoableActionPerformed(action);
        });
      }
    }
  }

  private static String commandName(TreeProblem candidate) {
    String message = candidate.getShortMessage();
    return GrazieBundle.message("auto.apply.fix.command.name", message);
  }

  private static boolean shouldAutoFix(Document document, TreeProblem problem) {
    if (!problem.match.autoFixCapable() || problem.getSuggestions().size() != 1) return false;

    List<StringOperation> changes = problem.getSuggestions().get(0).getChanges();
    TextContent text = problem.getText();
    List<TextRange> fileRanges = ContainerUtil.map(changes, c -> text.textRangeToFile(c.getRange()));

    for (Editor editor : EditorFactory.getInstance().getEditors(document, text.getContainingFile().getProject())) {
      for (Caret caret : editor.getCaretModel().getAllCarets()) {
        if (fileRanges.stream().anyMatch(r -> r.containsOffset(caret.getOffset()))) return false;
      }
    }

    ChangeTracker tracker = ChangeTracker.getInstance();
    return ContainerUtil.exists(fileRanges, r -> tracker.mayAutoChange(document, r)) &&
           !ContainerUtil.exists(fileRanges, r -> tracker.isExplicitlyUndone(document, r));
  }
}
