// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerHandler;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.util.GroovyChangeContextUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyUnusedImportUtil.unusedImports;

/**
 * @author Max Medvedev
 */
public class GroovyMoveClassToInnerHandler implements MoveClassToInnerHandler {
  private static final Logger LOG = Logger.getInstance(GroovyMoveClassToInnerHandler.class);

  @Override
  public PsiClass moveClass(@NotNull PsiClass aClass, @NotNull PsiClass targetClass) {
    if (!(aClass instanceof GrTypeDefinition)) return null;

    GroovyChangeContextUtil.encodeContextInfo(aClass);


    PsiDocComment doc = aClass.getDocComment();

    PsiElement brace = targetClass.getRBrace();
    PsiClass newClass = (PsiClass)targetClass.addBefore(aClass, brace);
    PsiElement sibling = newClass.getPrevSibling();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(targetClass.getProject());
    if (!org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isNewLine(sibling)) {
      targetClass.addBefore(factory.createLineTerminator("\n "), newClass);
    }
    else if (doc != null) {
      LOG.assertTrue(sibling != null);
      sibling.replace(factory.createLineTerminator(sibling.getText() + " "));
    }

    if (doc != null) {
      targetClass.addBefore(doc, newClass);
      targetClass.addBefore(factory.createLineTerminator("\n"), newClass);
    }

    if (targetClass.isInterface()) {
      PsiUtil.setModifierProperty(newClass, PsiModifier.PUBLIC, true);
    }
    else {
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, true);
    }
    GroovyChangeContextUtil.decodeContextInfo(newClass, null, null);

    return newClass;
  }


  @Override
  public List<PsiElement> filterImports(@NotNull List<UsageInfo> usageInfos, @NotNull Project project) {
    final List<PsiElement> importStatements = new ArrayList<>();
    if (!CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS) {
      filterUsagesInImportStatements(usageInfos, importStatements);
    }
    else {
      //rebind imports first
      Collections.sort(usageInfos, (o1, o2) -> PsiUtil.BY_POSITION.compare(o1.getElement(), o2.getElement()));
    }
    return importStatements;
  }

  private static void filterUsagesInImportStatements(final List<UsageInfo> usages, final List<PsiElement> importStatements) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext(); ) {
      UsageInfo usage = iterator.next();
      PsiElement element = usage.getElement();
      if (element == null) continue;
      GrImportStatement stmt = PsiTreeUtil.getParentOfType(element, GrImportStatement.class);
      if (stmt != null) {
        importStatements.add(stmt);
        iterator.remove();
      }
    }
  }

  @Override
  public void retargetClassRefsInMoved(@NotNull final Map<PsiElement, PsiElement> oldToNewElementsMapping) {
    for (final PsiElement newClass : oldToNewElementsMapping.values()) {
      if (!(newClass instanceof GrTypeDefinition)) continue;
      ((GrTypeDefinition)newClass).accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull GrReferenceExpression reference) {
          if (visitRef(reference)) return;
          super.visitReferenceExpression(reference);
        }

        @Override
        public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
          visitRef(refElement);
          super.visitCodeReferenceElement(refElement);
        }

        private boolean visitRef(GrReferenceElement reference) {
          PsiElement element = reference.resolve();
          if (element instanceof PsiClass) {
            for (PsiElement oldClass : oldToNewElementsMapping.keySet()) {
              if (PsiTreeUtil.isAncestor(oldClass, element, false)) {
                PsiClass newInnerClass =
                  findMatchingClass((PsiClass)oldClass, (PsiClass)oldToNewElementsMapping.get(oldClass), (PsiClass)element);
                try {
                  reference.bindToElement(newInnerClass);
                  return true;
                }
                catch (IncorrectOperationException ex) {
                  LOG.error(ex);
                }
              }
            }
          }
          return false;
        }
      });
    }
  }


  private static PsiClass findMatchingClass(final PsiClass classToMove, final PsiClass newClass, final PsiClass innerClass) {
    if (classToMove == innerClass) {
      return newClass;
    }
    PsiClass parentClass = findMatchingClass(classToMove, newClass, innerClass.getContainingClass());
    PsiClass newInnerClass = parentClass.findInnerClassByName(innerClass.getName(), false);
    assert newInnerClass != null;
    return newInnerClass;
  }

  @Override
  public void retargetNonCodeUsages(@NotNull final Map<PsiElement, PsiElement> oldToNewElementMap,
                                    @NotNull final NonCodeUsageInfo[] nonCodeUsages) {
    for (PsiElement newClass : oldToNewElementMap.values()) {
      if (!(newClass instanceof GrTypeDefinition)) continue;

      newClass.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(final PsiElement element) {
          super.visitElement(element);
          List<NonCodeUsageInfo> list = element.getCopyableUserData(MoveClassToInnerProcessor.ourNonCodeUsageKey);
          if (list != null) {
            for (NonCodeUsageInfo info : list) {
              for (int i = 0; i < nonCodeUsages.length; i++) {
                if (nonCodeUsages[i] == info) {
                  nonCodeUsages[i] = info.replaceElement(element);
                  break;
                }
              }
            }
            element.putCopyableUserData(MoveClassToInnerProcessor.ourNonCodeUsageKey, null);
          }
        }
      });
    }
  }

  @Override
  public void removeRedundantImports(PsiFile targetClassFile) {
    if (!(targetClassFile instanceof GroovyFile)) return;
    final GroovyFile file = (GroovyFile)targetClassFile;
    for (GrImportStatement unusedImport : unusedImports(file)) {
      file.removeImport(unusedImport);
    }
  }
}
