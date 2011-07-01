package org.jetbrains.plugins.groovy.template;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateOptionalProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
public class GroovyShortenFQNamesProcessor implements TemplateOptionalProcessor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.template.GroovyShortenFQNamesProcessor");

  public void processText(final Project project,
                          final Template template,
                          final Document document,
                          final RangeMarker templateRange,
                          final Editor editor) {
    if (!template.isToShortenLongNames()) return;

    try {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      final PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);
      if (file instanceof GroovyFile) {
        GrReferenceAdjuster.shortenReferences(file, templateRange.getStartOffset(), templateRange.getEndOffset(), true, false);
      }
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public String getOptionName() {
    return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names");
  }

  public boolean isEnabled(final Template template) {
    return template.isToShortenLongNames();
  }

  public void setEnabled(final Template template, final boolean value) {
  }

  @Override
  public boolean isVisible(Template template) {
    return false;
  }
}
