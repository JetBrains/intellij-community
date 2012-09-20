package org.jetbrains.plugins.groovy.cucumber.steps;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import cucumber.runtime.groovy.GroovySnippet;
import cucumber.runtime.snippets.SnippetGenerator;
import gherkin.formatter.model.Comment;
import gherkin.formatter.model.Step;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.StepDefinitionCreator;
import org.jetbrains.plugins.cucumber.psi.GherkinStep;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewScriptAction;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.cucumber.GrCucumberUtil;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrStepDefinitionCreator implements StepDefinitionCreator {
  @NotNull
  @Override
  public PsiFile createStepDefinitionContainer(@NotNull PsiDirectory dir, @NotNull String name) {
    String fileName = name + GroovyFileType.DEFAULT_EXTENSION;
    PsiFile file = GroovyTemplatesFactory.createFromTemplate(dir, name, fileName, NewScriptAction.GROOVY_SCRIPT_TMPL);
    assert file != null;
    return file;
  }

  @Override
  public boolean createStepDefinition(@NotNull GherkinStep step, @NotNull final PsiFile file) {
    if (!(file instanceof GroovyFile)) return false;

    final Project project = file.getProject();
    final VirtualFile vFile = ObjectUtils.assertNotNull(file.getVirtualFile());
    final OpenFileDescriptor descriptor = new OpenFileDescriptor(project, vFile);
    FileEditorManager.getInstance(project).getAllEditors(vFile);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();

    if (editor != null) {
      final TemplateManager templateManager = TemplateManager.getInstance(file.getProject());
      final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
      final Template template = templateManager.getActiveTemplate(editor);
      if (templateState != null && template != null) {
        templateState.gotoEnd();
      }
    }

    // snippet text
    final GrMethodCall element = buildStepDefinitionByStep(step);

    GrMethodCall methodCall = (GrMethodCall)((GroovyFile)file).addStatementBefore(element, null);
    GrReferenceAdjuster.shortenReferences(methodCall);
    methodCall = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(methodCall);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().createTemplateBuilder(methodCall);

    // regexp str
    GrLiteral pattern = GrCucumberUtil.getStepDefinitionPattern(methodCall);
    assert pattern != null;

    String patternText = pattern.getText();

    builder.replaceElement(pattern,
                           new TextRange(1, patternText.length() - 1),
                           patternText.substring(1, patternText.length() - 1));

    // block vars
    GrClosableBlock closure = methodCall.getClosureArguments()[0];
    final GrParameter[] blockVars = closure.getAllParameters();
    for (GrParameter var : blockVars) {
      PsiElement identifier = var.getNameIdentifierGroovy();
      builder.replaceElement(identifier, identifier.getText());
    }

    TemplateManager manager = TemplateManager.getInstance(project);

    final Editor editorToRunTemplate;
    if (editor == null) {
      editorToRunTemplate = QuickfixUtil.positionCursor(project, file, methodCall);
    }
    else {
      editorToRunTemplate = editor;
    }

    Template template = builder.buildTemplate();

    TextRange range = methodCall.getTextRange();
    editorToRunTemplate.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    editorToRunTemplate.getCaretModel().moveToOffset(range.getStartOffset());

    manager.startTemplate(editorToRunTemplate, template, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (brokenOff) return;

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(editorToRunTemplate.getDocument());
            final int offset = editorToRunTemplate.getCaretModel().getOffset();
            GrMethodCall methodCall = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, GrMethodCall.class, false);
            if (methodCall != null) {
              GrClosableBlock[] closures = methodCall.getClosureArguments();
              if (closures.length == 1) {
                GrClosableBlock closure = closures[0];
                selectBody(closure, editor);
              }
            }
          }
        });
      }
    });

    return true;
  }

  private static void selectBody(GrClosableBlock closure, Editor editor) {
    PsiElement arrow = closure.getArrow();
    PsiElement leftBound = PsiUtil.skipWhitespaces((arrow != null ? arrow : closure.getParameterList()).getNextSibling(), true);

    PsiElement rbrace = closure.getRBrace();
    PsiElement rightBound = rbrace != null ? PsiUtil.skipWhitespaces(rbrace.getPrevSibling(), false) : null;

    if (leftBound != null && rightBound != null) {
      editor.getSelectionModel().setSelection(leftBound.getTextRange().getStartOffset(), rightBound.getTextRange().getEndOffset());
      editor.getCaretModel().moveToOffset(leftBound.getTextRange().getStartOffset());
    }
  }

  private static GrMethodCall buildStepDefinitionByStep(@NotNull final GherkinStep step) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(step.getProject());
    final Step cucumberStep = new Step(new ArrayList<Comment>(), step.getKeyword().getText(), step.getStepName(), 0, null, null);
    SnippetGenerator generator = new SnippetGenerator(new GroovySnippet());
    String snippet =
      generator.getSnippet(cucumberStep).replace("PendingException", "cucumber.runtime.PendingException").replace("\\", "\\\\");

    return (GrMethodCall)factory.createStatementFromText(snippet, step);
  }

  @Override
  public boolean validateNewStepDefinitionFileName(@NotNull Project project, @NotNull String fileName) {
    return true;
  }

  @NotNull
  @Override
  public PsiDirectory getDefaultStepDefinitionFolder(@NotNull GherkinStep step) {
    final PsiFile featureFile = step.getContainingFile();
    return ObjectUtils.assertNotNull(featureFile.getParent());
  }

  @NotNull
  @Override
  public String getStepDefinitionFilePath(@NotNull PsiFile file) {
    final VirtualFile vFile = file.getVirtualFile();
    if (file instanceof GroovyFile && vFile != null) {
      String packageName = ((GroovyFile)file).getPackageName();
      if (StringUtil.isEmptyOrSpaces(packageName)) {
        return vFile.getNameWithoutExtension();
      }
      else {
        return packageName + "." + vFile.getNameWithoutExtension();
      }
    }
    return file.getName();
  }
}
