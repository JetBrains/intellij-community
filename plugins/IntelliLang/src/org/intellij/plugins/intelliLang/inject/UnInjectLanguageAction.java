package org.intellij.plugins.intelliLang.inject;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.FileContentUtil;
import org.jetbrains.annotations.NotNull;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.XmlAttributeInjection;
import org.intellij.plugins.intelliLang.inject.config.XmlTagInjection;
import org.intellij.plugins.intelliLang.inject.config.MethodParameterInjection;

import java.util.List;
import java.util.Iterator;
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

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiLanguageInjectionHost host = findInjectionHost(editor, file);
    Configuration configuration = Configuration.getInstance();
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
    } else if (host instanceof PsiMethod) {
      for (Iterator<MethodParameterInjection> it = configuration.getParameterInjections().iterator(); it.hasNext();) {
        MethodParameterInjection injection = it.next();
        if (injection.isApplicable((PsiMethod)host)) {
          it.remove();
          break;
        }
      }
    }
    configuration.configurationModified();
    FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
  }
}
