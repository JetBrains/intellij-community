/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

/**
 * @author Maxim.Medvedev
 */
public class MoveGroovyClassUtil {
  private MoveGroovyClassUtil() {
  }

  @Nullable
  public static PsiClass moveGroovyClass(@NotNull PsiClass aClass, @NotNull PsiDirectory moveDestination) {
    if (!aClass.getLanguage().equals(GroovyFileType.GROOVY_LANGUAGE)) return null;
    PsiFile file = aClass.getContainingFile();
    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);

    GroovyChangeContextUtil.encodeContextInfo(aClass);

    PsiClass newClass = null;
    if (file instanceof GroovyFile) {
      if (((GroovyFile)file).isScript() || ((GroovyFile)file).getClasses().length > 1) {
        correctSelfReferences(aClass, newPackage);
        final PsiClass created = ((GroovyFile)GroovyTemplatesFactory
          .createFromTemplate(moveDestination, aClass.getName(), aClass.getName() + NewGroovyActionBase.GROOVY_EXTENSION,
                              "GroovyClass.groovy")).getClasses()[0];
        if (aClass.getDocComment() == null) {
          final PsiDocComment createdDocComment = created.getDocComment();
          if (createdDocComment != null) {
            aClass.addBefore(createdDocComment, null);
          }
        }
        newClass = (PsiClass)created.replace(aClass);
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
          if (newPackage != null) {
            ((PsiClassOwner)file).setPackageName(newPackage.getQualifiedName());
          }
        }
      }
    }
    if (newClass != null) GroovyChangeContextUtil.decodeContextInfo(newClass, null, null);
    return newClass;
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
