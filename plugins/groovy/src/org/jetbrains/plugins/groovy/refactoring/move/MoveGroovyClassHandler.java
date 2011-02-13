/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

/**
 * @author Maxim.Medvedev
 */
public class MoveGroovyClassHandler implements MoveClassHandler {
  public PsiClass doMoveClass(@NotNull PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    if (!aClass.getLanguage().equals(GroovyFileType.GROOVY_LANGUAGE)) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;

    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);
    assert newPackage != null;

    PsiClass newClass = null;

    if (aClass instanceof GroovyScriptClass) {
      PackageWrapper targetPackage = new PackageWrapper(aClass.getManager(), newPackage.getQualifiedName());
      final VirtualFile sourceRoot =
        ProjectRootManager.getInstance(aClass.getProject()).getFileIndex().getSourceRootForFile(moveDestination.getVirtualFile());
      assert sourceRoot != null;

      final AutocreatingSingleSourceRootMoveDestination destination =
        new AutocreatingSingleSourceRootMoveDestination(targetPackage, sourceRoot);
      new MoveGroovyScriptProcessor(aClass.getProject(), new PsiElement[]{file}, destination, true, true, null).run();
    }
    else {
      //GroovyChangeContextUtil.encodeContextInfo(aClass);
      if (((GroovyFile)file).getClasses().length > 1) {
        correctSelfReferences(aClass, newPackage);
        String modifiersText = null;
        if (newPackage.getQualifiedName().equals(((GroovyFile)file).getPackageName())) {
          final GrPackageDefinition packageDefinition = ((GroovyFile)file).getPackageDefinition();
          if (packageDefinition != null) {
            final PsiModifierList modifierList = packageDefinition.getModifierList();
            if (modifierList != null) {
              modifiersText = modifierList.getText();
            }
          }
        }
        final PsiClass created = ((GroovyFile)GroovyTemplatesFactory
          .createFromTemplate(moveDestination, aClass.getName(), aClass.getName() + NewGroovyActionBase.GROOVY_EXTENSION,
                              "GroovyClass.groovy")).getClasses()[0];
        PsiDocComment docComment = aClass.getDocComment();
        if (docComment != null) {
          final PsiDocComment createdDocComment = created.getDocComment();
          if (createdDocComment != null) {
            createdDocComment.replace(docComment);
          }
          else {
            created.getContainingFile().addBefore(docComment, created);
          }
          docComment.delete();
        }
        newClass = (PsiClass)created.replace(aClass);
        if (modifiersText != null) {
          final GrPackageDefinition newPackageDefinition = (GrPackageDefinition)GroovyPsiElementFactory.getInstance(aClass.getProject())
            .createTopElementFromText(modifiersText + " package " + newPackage.getQualifiedName());
          ((GroovyFile)newClass.getContainingFile()).setPackage(newPackageDefinition);
        }
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
      else if (!moveDestination.equals(file.getContainingDirectory()) && moveDestination.findFile(file.getName()) != null) {
        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
        correctSelfReferences(aClass, newPackage);
        PsiFile newFile = moveDestination.findFile(file.getName());
        TreeElement enter = Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, aClass.getManager());
        newFile.getNode().addChild(enter);
        newClass = (GrTypeDefinition)newFile.add(aClass);
        aClass.delete();
      }
      else if (!moveDestination.equals(file.getContainingDirectory()) && moveDestination.findFile(file.getName()) == null) {
        if (!moveDestination.equals(file.getContainingDirectory())) {
          aClass.getManager().moveFile(file, moveDestination);
          newClass = ((GroovyFile)file).getClasses()[0];
          if (!newPackage.getQualifiedName().equals(((GroovyFile)file).getPackageName())) {
            ((PsiClassOwner)file).setPackageName(newPackage.getQualifiedName());
          }
        }
      }
      //if (newClass != null) GroovyChangeContextUtil.decodeContextInfo(newClass, null, null);
    }
    return newClass;
  }

  @Nullable
  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;
    return ((GroovyFile)file).getClasses().length > 1 ? clazz.getName() + "." + GroovyFileType.DEFAULT_EXTENSION : file.getName();
  }

  @Override
  public void prepareMove(@NotNull PsiClass aClass) {
    if (aClass.getContainingClass() instanceof GroovyFileBase) {
      GroovyChangeContextUtil.encodeContextInfo(getRealElement(aClass));
    }
  }

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    if (aClass.getContainingClass() instanceof GroovyFileBase) {
      GroovyChangeContextUtil.decodeContextInfo(getRealElement(aClass), null, null);
    }
  }

  private static PsiElement getRealElement(PsiClass aClass) {
    return aClass instanceof GroovyScriptClass ? aClass.getContainingFile() : aClass;
  }

  private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
    for (PsiReference reference : ReferencesSearch.search(oldClass, new LocalSearchScope(newClass)).findAll()) {
      reference.bindToElement(newClass);
    }
  }

  private static void correctSelfReferences(final PsiClass aClass, final PsiPackage newContainingPackage) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    if (aPackage == null) {
      return;
    }

    for (PsiReference reference : ReferencesSearch.search(aClass, new LocalSearchScope(aClass)).findAll()) {
      if (reference instanceof GrCodeReferenceElement) {
        final GrCodeReferenceElement qualifier = ((GrCodeReferenceElement)reference).getQualifier();
        if (qualifier != null) {
          qualifier.bindToElement(newContainingPackage);
        }
      }
    }
  }
}
