package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.AbstractBasicToClassNameDelegator;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author peter
 */
public class GroovyBasicToClassNameDelegator extends AbstractBasicToClassNameDelegator{

  @Override
  protected boolean isClassNameCompletionSupported(CompletionResultSet result, PsiFile file, PsiElement position) {
    if (!(file instanceof GroovyFileBase)) {
      return false;
    }

    if (!(position.getParent() instanceof GrCodeReferenceElement)) return false;
    if (((GrCodeReferenceElement)position.getParent()).getQualifier() != null) return false;

    final String s = result.getPrefixMatcher().getPrefix();
    if (StringUtil.isEmpty(s) || !Character.isUpperCase(s.charAt(0))) return false;
    return true;
  }

  @Override
  protected void updateProperties(LookupElement lookupElement) {
    if (lookupElement instanceof JavaPsiClassReferenceElement) {
      ((JavaPsiClassReferenceElement)lookupElement).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
    }
  }

}
