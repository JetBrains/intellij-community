package org.jetbrains.plugins.groovy.cucumber.steps;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
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
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

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
  public boolean createStepDefinition(@NotNull GherkinStep step, @NotNull PsiFile file) {
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

    GrStatement statement = ((GroovyFile)file).addStatementBefore(element, null);
    statement = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(statement);
    GrReferenceAdjuster.shortenReferences(statement);
    return true;
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
      return ((GroovyFile)file).getPackageName() + "." + vFile.getNameWithoutExtension();
    }
    return file.getName();
  }
}
