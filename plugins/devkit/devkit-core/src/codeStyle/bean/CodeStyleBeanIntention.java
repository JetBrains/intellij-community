// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.codeStyle.bean;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("SameParameterValue")
public class CodeStyleBeanIntention implements IntentionAction {
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getText() {
    return "Generate CodeStyle methods";
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return "Language plugin";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiClass beanClass = findBeanClass(file);
    if (beanClass != null) {
      int offset = editor.getCaretModel().getOffset();
      if (beanClass.getTextRange().contains(offset)) {
        PsiElement currElement = file.findElementAt(offset);
        if (currElement != null && currElement.getParent() == beanClass) {
          ASTNode leftBrace = TreeUtil.findSibling(currElement.getNode(), JavaTokenType.RBRACE);
          ASTNode rightBrace = TreeUtil.findSiblingBackward(currElement.getNode(), JavaTokenType.LBRACE);
          return leftBrace != null && rightBrace != null;
        }
      }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClass beanClass = findBeanClass(file);
    if (beanClass != null) {
      generateAccessors(beanClass, editor, file);
    }
  }

  @Nullable
  private static PsiClass findBeanClass(@NotNull PsiFile file) {
    if (file instanceof PsiJavaFile) {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      for (PsiClass javaClass : classes) {
        if (isCodeStyleBean(javaClass)) return javaClass;
      }
    }
    return null;
  }

  private static boolean isCodeStyleBean(@NotNull PsiClass javaClass) {
    PsiReferenceList extendsList = javaClass.getExtendsList();
    if (extendsList != null) {
      for (PsiClassType type : extendsList.getReferencedTypes()) {
        if ("CodeStyleBean".equals(type.getClassName())) return true;
      }
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  private static void generateAccessors(@NotNull PsiClass beanClass, @NotNull Editor editor, @NotNull PsiFile file) {
    Project project = editor.getProject();
    if (project != null) {
      int offset = editor.getCaretModel().getOffset();
      Language language = getLanguage(beanClass, file);
      if (language != null) {
        CodeStyleBeanGenerator beanGenerator = new CodeStyleBeanGenerator(file, beanClass, language);
        String accessorsText = beanGenerator.generateBeanMethods();
        editor.getDocument().insertString(offset, accessorsText);
        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        CodeStyleManager.getInstance(project).reformatText(file, offset, offset + accessorsText.length());
        for (String requiredImport : beanGenerator.getImports()) {
          addImport(file, beanClass, requiredImport);
        }
      }
    }
  }

  private static void addImport(@NotNull PsiFile file, @NotNull PsiClass beanClass, @NotNull String classFQN) {
    PsiClass psiClass = CodeStyleBeanGenerator.resolveClass(classFQN, file);
    if (psiClass != null) {
      JavaCodeStyleManager.getInstance(beanClass.getProject()).addImport((PsiJavaFile)file, psiClass);
    }
  }

  @Nullable
  private static Language getLanguage(@NotNull PsiClass beanClass, @NotNull PsiFile file) {
    for (PsiMethod method : beanClass.getMethods()) {
      if ("getLanguage".equals(method.getName())) {
        PsiCodeBlock body = method.getBody();
        if (body != null) {
          for (PsiStatement statement : body.getStatements()) {
            if (statement instanceof PsiReturnStatement) {
               PsiExpression retValue = ((PsiReturnStatement)statement).getReturnValue();
               if (retValue instanceof PsiReferenceExpression) {
                 PsiElement psiLangClass = retValue.getFirstChild();
                 if (psiLangClass != null) {
                   PsiClass resolved = CodeStyleBeanGenerator.resolveClass(psiLangClass.getText(), file);
                   if (resolved != null) {
                     try {
                       Class langClass = Class.forName(resolved.getQualifiedName());
                       if (Language.class.isAssignableFrom(langClass)) {
                         //noinspection unchecked
                         return Language.findInstance(langClass);
                       }
                     }
                     catch (ClassNotFoundException e) {
                       // Ignore
                     }
                   }
                 }
               }
            }
          }
        }
      }
    }
    return null;
  }
}
