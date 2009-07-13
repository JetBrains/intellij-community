package org.intellij.plugins.intelliLang.inject;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.FileContentUtil;
import com.intellij.util.IncorrectOperationException;
import static org.intellij.plugins.intelliLang.inject.InjectLanguageAction.findInjectionHost;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class UnInjectLanguageAction implements IntentionAction {

  @NotNull
  public String getText() {
    return "Un-inject Language";
  }

  @NotNull
  public String getFamilyName() {
    return InjectLanguageAction.INJECT_LANGUAGE_FAMILY;
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
    try {
      for (LanguageInjectorSupport support : Extensions.getExtensions(LanguageInjectorSupport.EP_NAME)) {
        if (support.removeInjectionInPlace(host)) return;
      }
      final TemporaryPlacesRegistry places = TemporaryPlacesRegistry.getInstance(project);
      for (Pair<SmartPsiElementPointer<PsiLanguageInjectionHost>, InjectedLanguage> pair : places.getTempInjectionsSafe()) {
        if (pair.first.getElement() == host) {
          places.removeTempInjection(pair);
        }
      }
    }
    finally {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

}
