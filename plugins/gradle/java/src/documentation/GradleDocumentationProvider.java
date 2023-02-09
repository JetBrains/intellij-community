// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.documentation;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleDocumentationBundle;
import org.jetbrains.plugins.groovy.dsl.GdslNamedParameter;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;

/**
 * @author Vladislav.Soroka
 */
public class GradleDocumentationProvider implements DocumentationProvider {

  @Nullable
  @Override
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    PsiFile file = element.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;
    if (element instanceof GrLightVariable var) {
      PsiElement navigationElement = var.getNavigationElement();
      return navigationElement.getUserData(NonCodeMembersHolder.DOCUMENTATION);
    }
    return null;
  }

  @Nullable
  @Override
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    PsiFile file = element.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;
    return element instanceof GrLiteral ? findDoc(element, ((GrLiteral)element).getValue()) : null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    final PsiFile file = element.getContainingFile();
    if (file == null || !FileUtilRt.extensionEquals(file.getName(), GradleConstants.EXTENSION)) return null;
    final String doc = findDoc(element, object);
    return !StringUtil.isEmpty(doc) ? new GdslNamedParameter(String.valueOf(object), doc, element, null) : null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return JavaDocUtil.findReferenceTarget(psiManager, link, context);
  }

  @Nullable
  private static @NlsSafe String findDoc(@Nullable PsiElement element, Object argValue) {
    String result = null;
    if (element instanceof GrLiteral grLiteral) {
      PsiElement stmt = PsiTreeUtil.findFirstParent(grLiteral, psiElement -> psiElement instanceof GrCall);
      if (stmt instanceof GrCall grCall) {
        PsiMethod psiMethod = grCall.resolveMethod();
        if (psiMethod != null && psiMethod.getContainingClass() != null) {
          String qualifiedName = psiMethod.getContainingClass().getQualifiedName();
          if (grLiteral.getParent() instanceof GrNamedArgument namedArgument) {
            String key = StringUtil.join(new String[]{
              "gradle.documentation",
              qualifiedName,
              psiMethod.getName(),
              namedArgument.getLabelName(),
              String.valueOf(argValue),
            }, "."
            );

            result = GradleDocumentationBundle.INSTANCE.messageOrDefault(key, "");
          }
        }
      }
    }
    return result;
  }
}
