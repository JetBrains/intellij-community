package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

/**
 * @author Dmitry Avdeev
 */
public class UnInjectLanguageAction extends InjectLanguageAction {

  @NotNull
  public String getText() {
    return "Un-inject Language";
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    if (host == null) {
      return false;
    }
    List<Pair<PsiElement, TextRange>> injectedPsi = host.getInjectedPsi();
    return injectedPsi != null && !injectedPsi.isEmpty();
  }

  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    final Configuration configuration = Configuration.getInstance();
    final ArrayList<BaseInjection> injectionsToRemove = new ArrayList<BaseInjection>();
    final ArrayList<PsiAnnotation> annotationsToRemove = new ArrayList<PsiAnnotation>();
    if (host instanceof XmlAttributeValue) {
      for (final XmlAttributeInjection injection : configuration.getAttributeInjections()) {
        if (injection.isApplicable((XmlAttributeValue)host)) {
          injectionsToRemove.add(injection);
        }
      }
    } else if (host instanceof XmlText) {
      final XmlTag tag = ((XmlText)host).getParentTag();
      if (tag != null) {
        for (XmlTagInjection injection : configuration.getTagInjections()) {
          if (injection.isApplicable(tag)) {
            injectionsToRemove.add(injection);
          }
        }
      }
    } else if (host instanceof PsiLiteralExpression) {
      ConcatenationInjector.processLiteralExpressionInjectionsInner(configuration, new Processor<ConcatenationInjector.Info>() {
        public boolean process(final ConcatenationInjector.Info info) {
          final PsiAnnotation[] annotations = AnnotationUtilEx.getAnnotationFrom(info.owner, configuration.getLanguageAnnotationPair(), true);
          annotationsToRemove.addAll(Arrays.asList(annotations));
          for (MethodParameterInjection injection : info.injections) {
            if (injection.isApplicable(info.method)) {
              injectionsToRemove.add(injection);
            }
          }
          return true;
        }
      }, host);
    }
    if (!injectionsToRemove.isEmpty() || !annotationsToRemove.isEmpty()) {
      final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(annotationsToRemove, new NullableFunction<PsiAnnotation, PsiFile>() {
        public PsiFile fun(final PsiAnnotation psiAnnotation) {
          return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
        }
      });
      final UndoableAction action = new UndoableAction() {
        public void undo() throws UnexpectedUndoException {
          for (BaseInjection injection : injectionsToRemove) {
            if (injection instanceof XmlTagInjection) {
              configuration.getTagInjections().add((XmlTagInjection)injection);
            }
            else if (injection instanceof XmlAttributeInjection) {
              configuration.getAttributeInjections().add((XmlAttributeInjection)injection);
            }
            else if (injection instanceof MethodParameterInjection) {
              configuration.getParameterInjections().add((MethodParameterInjection)injection);
            }
          }
          configuration.configurationModified();
          FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
        }

        public void redo() throws UnexpectedUndoException {
          configuration.getTagInjections().removeAll(injectionsToRemove);
          configuration.getAttributeInjections().removeAll(injectionsToRemove);
          configuration.getParameterInjections().removeAll(injectionsToRemove);
          configuration.configurationModified();
          FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);

        }

        public DocumentReference[] getAffectedDocuments() {
          return DocumentReference.EMPTY_ARRAY;
        }

        public boolean isComplex() {
          return true;
        }
      };
      new WriteCommandAction(project, psiFiles.toArray(new PsiFile[psiFiles.size()])) {
        @Override
        protected void run(final Result result) throws Throwable {
          for (PsiAnnotation annotation : annotationsToRemove) {
            annotation.delete();
          }
          action.redo();
          UndoManager.getInstance(project).undoableActionPerformed(action);
        }
      }.execute();
    }
  }
}
