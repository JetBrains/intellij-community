package org.intellij.plugins.intelliLang.inject;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.util.ArrayUtil;
import org.intellij.plugins.intelliLang.Configuration;
import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author gregsh
 */
public class CommentLanguageInjector implements MultiHostInjector {

  private final LanguageInjectionSupport[] mySupports;
  private final LanguageInjectionSupport myInjectorSupport = new AbstractLanguageInjectionSupport() {
    @NotNull
    @Override
    public String getId() {
      return "comment";
    }

    @Override
    public boolean isApplicableTo(PsiLanguageInjectionHost host) {
      return true;
    }

    @NotNull
    @Override
    public Class[] getPatternClasses() {
      return ArrayUtil.EMPTY_CLASS_ARRAY;
    }
  };


  /** @noinspection UnusedParameters*/
  public CommentLanguageInjector(Configuration configuration) {
    List<LanguageInjectionSupport> supports = new ArrayList<>(InjectorUtils.getActiveInjectionSupports());
    supports.add(myInjectorSupport);
    mySupports = ArrayUtil.toObjectArray(supports, LanguageInjectionSupport.class);
  }

  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(PsiLanguageInjectionHost.class);
  }

  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement context) {
    if (!(context instanceof PsiLanguageInjectionHost) || context instanceof PsiComment) return;
    if (!((PsiLanguageInjectionHost)context).isValidHost()) return;
    PsiLanguageInjectionHost host = (PsiLanguageInjectionHost)context;

    boolean applicableFound = false;
    for (LanguageInjectionSupport support : mySupports) {
      if (!support.isApplicableTo(host)) continue;
      if (support == myInjectorSupport && applicableFound) continue;
      applicableFound = true;

      BaseInjection injection = support.findCommentInjection(host, null);
      if (injection == null) continue;
      if (!InjectorUtils.registerInjectionSimple(host, injection, support, registrar)) continue;
      return;
    }
  }
}
