/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.actions.NewGroovyActionBase;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.refactoring.GroovyChangeContextUtil;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author Maxim.Medvedev
 */
public class MoveGroovyClassHandler implements MoveClassHandler {
  Logger LOG = Logger.getInstance(MoveGroovyClassHandler.class);

  @Override
  public PsiClass doMoveClass(@NotNull PsiClass aClass, @NotNull PsiDirectory moveDestination) throws IncorrectOperationException {
    if (!aClass.getLanguage().equals(GroovyLanguage.INSTANCE)) return null;
    PsiFile file = aClass.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;

    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(moveDestination);
    LOG.assertTrue(newPackage != null);

    PsiClass newClass = null;

    final String newPackageName = newPackage.getQualifiedName();
    if (aClass instanceof GroovyScriptClass) {
      final PsiClass[] classes = ((GroovyFile)file).getClasses();
      if (classes.length == 1) {
        if (!moveDestination.equals(file.getContainingDirectory())) {
          MoveFilesOrDirectoriesUtil.doMoveFile(file, moveDestination);
          ((PsiClassOwner)file).setPackageName(newPackageName);
        }
        return ((GroovyFile)file).getScriptClass();
      }
      //script class is moved the first from the file due to MoveClassOrPackageProcessor:88 (element sort)
      correctSelfReferences(aClass, newPackage);
      final GroovyFile newFile = generateNewScript((GroovyFile)file, newPackage);

      for (PsiElement child : file.getChildren()) {
        if (!(child instanceof GrTopStatement || child instanceof PsiComment)) continue;
        if (child instanceof PsiClass || child instanceof GrImportStatement || child instanceof GrPackageDefinition) continue;
        if (child instanceof GrDocComment) {
          final GrDocCommentOwner owner = GrDocCommentUtil.findDocOwner((GrDocComment)child);
          if (owner instanceof PsiClass) continue;
        }
        child.delete();
      }


      if (!moveDestination.equals(file.getContainingDirectory())) {
        moveDestination.add(newFile);

        //aClass.getManager().moveFile(newFile, moveDestination);
      }
      newClass = newFile.getClasses()[0];
      correctOldClassReferences(newClass, aClass);
    }
    else {
      if (!moveDestination.equals(file.getContainingDirectory()) && moveDestination.findFile(file.getName()) != null) {
        // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
        correctSelfReferences(aClass, newPackage);
        PsiFile newFile = moveDestination.findFile(file.getName());
        final FileASTNode fileNode = newFile.getNode();
        fileNode.addChild(Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n\n", 0, 2, null, aClass.getManager()));
        final PsiDocComment docComment = aClass.getDocComment();
        if (docComment != null) {
          newFile.add(docComment);
          fileNode.addChild(Factory.createSingleLeafElement(GroovyTokenTypes.mNLS, "\n", 0, 1, null, aClass.getManager()));
        }
        newClass = (GrTypeDefinition)newFile.add(aClass);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
      else if (((GroovyFile)file).getClasses().length > 1) {
        correctSelfReferences(aClass, newPackage);

        final PsiFile fromTemplate =
          GroovyTemplatesFactory.createFromTemplate(moveDestination, aClass.getName(), aClass.getName() + NewGroovyActionBase.GROOVY_EXTENSION, GroovyTemplates.GROOVY_CLASS, true);
        final PsiClass created = ((GroovyFile)fromTemplate).getClasses()[0];
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
        setPackageDefinition((GroovyFile)file, (GroovyFile)newClass.getContainingFile(), newPackageName);
        correctOldClassReferences(newClass, aClass);
        aClass.delete();
      }
    }
    return newClass;
  }

  private static void setPackageDefinition(GroovyFile file, GroovyFile newFile, String newPackageName) {
    String modifiersText = null;

    final GrPackageDefinition packageDefinition = file.getPackageDefinition();
    if (packageDefinition != null) {
      final PsiModifierList modifierList = packageDefinition.getModifierList();
      if (modifierList != null) {
        modifiersText = modifierList.getText().trim();
      }
    }

    if (modifiersText != null && !modifiersText.isEmpty()) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());
      final GrPackageDefinition newPackageDefinition = (GrPackageDefinition)factory.createTopElementFromText(modifiersText + " package " + newPackageName);
      newFile.setPackage(newPackageDefinition);
    }
    else {
      newFile.setPackageName(newPackageName);
    }
  }

  private static GroovyFile generateNewScript(GroovyFile file, PsiPackage newPackage) {
    for (GrImportStatement importStatement : file.getImportStatements()) {
      importStatement.delete();
    }
    final GroovyFile newFile = GroovyPsiElementFactory.getInstance(file.getProject()).createGroovyFile("", true, null);

    newFile.addRange(file.getFirstChild(), file.getLastChild());

    final PsiClass[] newFileClasses = newFile.getClasses();
    for (PsiClass psiClass : newFileClasses) {
      if (psiClass instanceof GroovyScriptClass) continue;
      final GrDocComment docComment = GrDocCommentUtil.findDocComment((GrDocCommentOwner)psiClass);
      if (docComment != null) docComment.delete();
      psiClass.delete();
    }

    final GrPackageDefinition packageDefinition = newFile.getPackageDefinition();
    if (packageDefinition != null) packageDefinition.delete();

    PsiElement cur = newFile.getFirstChild();
    while (cur != null && PsiImplUtil.isWhiteSpaceOrNls(cur)) {
      cur = cur.getNextSibling();
    }
    if (cur != null && cur != newFile.getFirstChild()) {
      cur = cur.getPrevSibling();
      newFile.deleteChildRange(newFile.getFirstChild(), cur);
    }

    cur = newFile.getLastChild();
    while (cur != null && PsiImplUtil.isWhiteSpaceOrNls(cur)) {
      cur = cur.getPrevSibling();
    }
    if (cur != null && cur != newFile.getLastChild()) {
      cur = cur.getNextSibling();
      newFile.deleteChildRange(cur, newFile.getLastChild());
    }

    newFile.setName(file.getName());
    setPackageDefinition(file, newFile, newPackage.getQualifiedName());

    GroovyChangeContextUtil.decodeContextInfo(newFile, null, null);
    return newFile;
  }

  @Override
  @Nullable
  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof GroovyFile)) return null;
    return ((GroovyFile)file).getClasses().length > 1 ? clazz.getName() + "." + GroovyFileType.DEFAULT_EXTENSION : file.getName();
  }

  @Override
  public void preprocessUsages(Collection<UsageInfo> results) {
    //remove all alias-imported usages from collection
    for (Iterator<UsageInfo> iterator = results.iterator(); iterator.hasNext(); ) {
      UsageInfo info = iterator.next();
      if (info == null) {
        LOG.debug("info==null");
        continue;
      }
      final PsiReference ref = info.getReference();
      if (ref==null) continue;

      final PsiElement element = ref.getElement();
      if (!(element instanceof GrReferenceElement)) continue;

      final GroovyResolveResult resolveResult = ((GrReferenceElement)element).advancedResolve();
      final PsiElement context = resolveResult.getCurrentFileResolveContext();
      if (!(context instanceof GrImportStatement)) continue;

      if (!((GrImportStatement)context).isAliasedImport()) continue;

      iterator.remove();
    }
  }

  @Override
  public void prepareMove(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof GroovyFileBase) {
      GroovyChangeContextUtil.encodeContextInfo(getRealElement(aClass));
    }
  }

  @Override
  public void finishMoveClass(@NotNull PsiClass aClass) {
    if (aClass.getContainingFile() instanceof GroovyFileBase) {
      GroovyChangeContextUtil.decodeContextInfo(getRealElement(aClass), null, null);
    }
  }

  private static PsiElement getRealElement(PsiClass aClass) {
    return aClass instanceof GroovyScriptClass ? aClass.getContainingFile() : aClass;
  }

  private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
    final Collection<PsiReference> all = ReferencesSearch.search(oldClass, new LocalSearchScope(newClass.getContainingFile())).findAll();
    for (PsiReference reference : all) {
      final PsiElement element = reference.getElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof GrImportStatement && !((GrImportStatement)parent).isStatic()) {
        parent.delete();
      }
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
