/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author ven
 */
public class GroovyFindUsagesProvider implements FindUsagesProvider
{
  private static class WordsScanner extends DefaultWordsScanner
  {
    public WordsScanner()
    {
      super(new GroovyLexer(), GroovyElementTypes.IDENTIFIER_SET, GroovyElementTypes.COMMENT_SET, GroovyElementTypes.STRING_LITERAL_SET);
    }
  }

  public static final GroovyFindUsagesProvider INSTANCE = new GroovyFindUsagesProvider();

  private GroovyFindUsagesProvider()
  {
  }

  @Nullable
  public WordsScanner getWordsScanner()
  {
    return new WordsScanner();
  }

  public boolean canFindUsagesFor(@NotNull PsiElement psiElement)
  {
    return psiElement instanceof PsiClass ||
        psiElement instanceof PsiMethod ||
        psiElement instanceof GrVariable;
  }

  @Nullable
  public String getHelpId(@NotNull PsiElement psiElement)
  {
    return null;
  }

  @NotNull
  public String getType(@NotNull PsiElement element)
  {
    if (element instanceof PsiClass) return "class";
    if (element instanceof PsiMethod) return "method";
    if (element instanceof PsiField) return "field";
    if (element instanceof PsiParameter) return "parameter";
    if (element instanceof PsiVariable) return "variable";
    return "";
  }

  @NotNull
  public String getDescriptiveName(@NotNull PsiElement element)
  {
    if (element instanceof PsiClass)
    {
      final PsiClass aClass = (PsiClass) element;
      String qName = aClass.getQualifiedName();
      return qName == null ? "" : qName;
    } else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) element;
      String result = PsiFormatUtil.formatMethod(method,
                                                    PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                                    PsiFormatUtil.SHOW_TYPE);
      final PsiClass clazz = method.getContainingClass();
      if (clazz != null) {
        result += "of" + getDescriptiveName(clazz);
      }

      return result;
    } else if (element instanceof PsiVariable) {
      final String name = ((PsiVariable) element).getName();
      if (name != null) {
        return name;
      }
    }

    return "";
  }

  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName)
  {
    if (element instanceof PsiClass)
    {
      String name = ((PsiClass) element).getQualifiedName();
      if (name == null || !useFullName)
      {
        name = ((PsiClass) element).getName();
      }
      if (name != null) return name;
    }
    else if (element instanceof PsiMethod) {
      return PsiFormatUtil.formatMethod((PsiMethod) element,
                                        PsiSubstitutor.EMPTY,
                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                        PsiFormatUtil.SHOW_TYPE);

    }
    else if (element instanceof PsiVariable) {
      final String name = ((PsiVariable) element).getName();
      if (name != null) {
        return name;
      }
    }

    return "";
  }
}
