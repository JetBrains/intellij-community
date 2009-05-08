package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.util.AnnotationUtilEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    final Configuration configuration = Configuration.getInstance();
    if (host instanceof XmlAttributeValue) {
      for (Iterator<XmlAttributeInjection> it = configuration.getAttributeInjections().iterator(); it.hasNext();) {
        XmlAttributeInjection injection = it.next();
        if (injection.isApplicable((XmlAttributeValue)host)) {
          it.remove();
          break;
        }
      }
    } else if (host instanceof XmlTag) {
      for (Iterator<XmlTagInjection> it = configuration.getTagInjections().iterator(); it.hasNext();) {
        XmlTagInjection injection = it.next();
        if (injection.isApplicable((XmlTag)host)) {
          it.remove();
          break;
        }
      }
    } else if (host instanceof PsiLiteralExpression) {
      final ArrayList<PsiAnnotation> annotationsToRemove = new ArrayList<PsiAnnotation>();
      final ArrayList<MethodParameterInjection> injectionsToRemove = new ArrayList<MethodParameterInjection>();
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
      if (!injectionsToRemove.isEmpty()) {
        new WriteCommandAction.Simple(project) {
          public void run() {
            for (MethodParameterInjection injection : injectionsToRemove) {
              configuration.getParameterInjections().remove(injection);
            }
          }
        }.execute();
      }
      if (!annotationsToRemove.isEmpty()) {
        final List<PsiFile> psiFiles = ContainerUtil.mapNotNull(annotationsToRemove, new NullableFunction<PsiAnnotation, PsiFile>() {
          public PsiFile fun(final PsiAnnotation psiAnnotation) {
            return psiAnnotation instanceof PsiCompiledElement ? null : psiAnnotation.getContainingFile();
          }
        });
        new WriteCommandAction.Simple(project, psiFiles.toArray(new PsiFile[psiFiles.size()])) {
          protected void run() throws Throwable {
            for (PsiAnnotation annotation : annotationsToRemove) {
              annotation.delete();
            }
          }
        }.execute();
      }
    }
    configuration.configurationModified();
    FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
  }
}
