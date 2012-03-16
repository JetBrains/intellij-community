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
package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.isImportToJavaOrJavax;

/**
 * @author ven
 */
public class GroovyImportOptimizer implements ImportOptimizer {

  @NotNull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file, false);
  }

  public List<GrImportStatement> findUnusedImports(GroovyFile file, Set<GrImportStatement> usedImports) {
    return new MyProcessor(file, true)
      .findUnusedImports(new HashSet<String>(), new HashSet<String>(), usedImports, new HashSet<String>(), new HashSet<String>());
  }

  public boolean supports(PsiFile file) {
    return file instanceof GroovyFile;
  }

  private class MyProcessor implements Runnable {
    private final PsiFile myFile;
    private final boolean myRemoveUnusedOnly;

    private MyProcessor(PsiFile file, boolean removeUnusedOnly) {
      myFile = file;
      myRemoveUnusedOnly = removeUnusedOnly;
    }

    public void run() {
      if (!(myFile instanceof GroovyFile)) return;

      GroovyFile file = ((GroovyFile)myFile);
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      final Document document = documentManager.getDocument(file);
      if (document != null) {
        documentManager.commitDocument(document);
      }
      final Set<String> importedClasses = new LinkedHashSet<String>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<String>();
      final Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
      final Set<String> implicitlyImported = new LinkedHashSet<String>();
      final HashSet<String> innerClasses = new HashSet<String>();
      final List<GrImportStatement> oldImports =
        findUnusedImports(importedClasses, staticallyImportedMembers, usedImports, implicitlyImported, innerClasses);
      if (myRemoveUnusedOnly) {
        for (GrImportStatement oldImport : oldImports) {
          if (!usedImports.contains(oldImport)) {
            file.removeImport(oldImport);
          }
        }
        return;
      }

      Map<String, String> annotations = new HashMap<String, String>();

      // Getting aliased imports
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());
      ArrayList<GrImportStatement> aliased = new ArrayList<GrImportStatement>();
      for (GrImportStatement oldImport : oldImports) {
        if (oldImport.isAliasedImport() && usedImports.contains(oldImport)) {
          aliased.add(factory.createImportStatementFromText(oldImport.getText()));
        }
        else {
          String importReference = getImportReferenceText(oldImport);
          GrModifierList annotationList = oldImport.getAnnotationList();
          if (importReference != null && annotationList != null) {
            annotations.put(importReference, annotationList.getText());
          }
        }
      }

      // Add new import statements
      GrImportStatement[] newImports = prepare(importedClasses, staticallyImportedMembers, implicitlyImported, innerClasses);
      if (oldImports.isEmpty() && newImports.length == 0 && aliased.isEmpty()) {
        return;
      }

      GroovyFile tempFile = (GroovyFile)PsiFileFactory.getInstance(file.getProject()).createFileFromText("a.groovy", "");

      for (GrImportStatement aliasedImport : aliased) {
        tempFile.addImport(aliasedImport);
      }
      for (GrImportStatement newImport : newImports) {
        String imported = getImportReferenceText(newImport);
        String annos = annotations.get(imported);
        if (imported != null && StringUtil.isNotEmpty(annos)) {
          newImport = factory.createImportStatementFromText(annos + " " + newImport.getText());
        }

        tempFile.addImport(newImport);
      }

      String oldText = oldImports.isEmpty() ? "" :
                       myFile.getText().substring(oldImports.get(0).getTextRange().getStartOffset(),
                                                  oldImports.get(oldImports.size() - 1).getTextRange().getEndOffset());
      if (tempFile.getText().trim().equals(oldText)) {
        return;
      }

      for (GrImportStatement statement : tempFile.getImportStatements()) {
        file.addImport(statement);
      }

      file.removeImport(file.addImport(factory.createImportStatementFromText("import xxxx"))); //to remove trailing whitespaces

      for (GrImportStatement importStatement : oldImports) {
        file.removeImport(importStatement);
      }
    }

    @Nullable
    String getImportReferenceText(GrImportStatement statement) {
      GrCodeReferenceElement importReference = statement.getImportReference();
      if (importReference != null) {
        return statement.getText().substring(importReference.getStartOffsetInParent());
      }
      return null;
    }

    public List<GrImportStatement> findUnusedImports(final Set<String> importedClasses,
                                                     final Set<String> staticallyImportedMembers,
                                                     final Set<GrImportStatement> usedImports,
                                                     final Set<String> implicitlyImported,
                                                     final Set<String> innerClasses) {
      if (!(myFile instanceof GroovyFile)) return Collections.emptyList();

      ((GroovyFile)myFile).accept(new GroovyRecursiveElementVisitor() {
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          visitRefElement(refElement);
          super.visitCodeReferenceElement(refElement);
        }

        public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
          visitRefElement(referenceExpression);
          super.visitReferenceExpression(referenceExpression);
        }

        private void visitRefElement(GrReferenceElement refElement) {
          final GroovyResolveResult[] resolveResults = refElement.multiResolve(false);
          for (GroovyResolveResult resolveResult : resolveResults) {
            final GroovyPsiElement context = resolveResult.getCurrentFileResolveContext();
            final PsiElement element = resolveResult.getElement();
            if (element == null) return;

            if (context instanceof GrImportStatement) {
              final GrImportStatement importStatement = (GrImportStatement)context;

              usedImports.add(importStatement);
              if (!importStatement.isAliasedImport()) {
                String importedName = null;
                if (importStatement.isOnDemand()) {

                  if (importStatement.isStatic()) {
                    if (element instanceof PsiMember) {
                      final PsiMember member = (PsiMember)element;
                      final PsiClass clazz = member.getContainingClass();
                      if (clazz != null) {
                        final String classQName = clazz.getQualifiedName();
                        if (classQName != null) {
                          final String name = member.getName();
                          if (name != null) {
                            importedName = classQName + "." + name;
                          }
                        }
                      }
                    }
                  }
                  else {
                    importedName = getTargetQualifiedName(element);
                  }
                }
                else {
                  final GrCodeReferenceElement importReference = importStatement.getImportReference();
                  if (importReference != null) {
                    importedName = PsiUtil.getQualifiedReferenceText(importReference);
                  }
                }

                if (importedName == null) return;

                if (importStatement.isStatic()) {
                  staticallyImportedMembers.add(importedName);
                }
                else {
                  importedClasses.add(importedName);
                  if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() != null) {
                    innerClasses.add(importedName);
                  }
                }
              }
            }
            else if (context == null && !(refElement.getParent() instanceof GrImportStatement) && refElement.getQualifier() == null) {
              final String qname = getTargetQualifiedName(element);
              if (qname != null) {
                implicitlyImported.add(qname);
                importedClasses.add(qname);
              }
            }
          }
        }
      });


      return getValidImportStatements((GroovyFile)myFile);
    }

    @Nullable
    private String getTargetQualifiedName(PsiElement element) {
      if (element instanceof PsiClass) {
        return ((PsiClass)element).getQualifiedName();
      }
      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        return ((PsiMethod)element).getContainingClass().getQualifiedName();
      }
      return null;
    }

    private GrImportStatement[] prepare(Set<String> importedClasses,
                                        Set<String> staticallyImportedMembers,
                                        Set<String> implicitlyImported,
                                        Set<String> innerClasses) {
      final Project project = myFile.getProject();
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      TObjectIntHashMap<String> packageCountMap = new TObjectIntHashMap<String>();
      TObjectIntHashMap<String> classCountMap = new TObjectIntHashMap<String>();

      for (String importedClass : importedClasses) {
        if (implicitlyImported.contains(importedClass) || innerClasses.contains(importedClass)) continue;

        final String packageName = StringUtil.getPackageName(importedClass);

        if (!packageCountMap.containsKey(packageName)) packageCountMap.put(packageName, 0);
        packageCountMap.increment(packageName);
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = StringUtil.getPackageName(importedMember);
        if (!classCountMap.containsKey(className)) classCountMap.put(className, 0);
        classCountMap.increment(className);
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<String>();
      final List<GrImportStatement> result = new ArrayList<GrImportStatement>();
      packageCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        public boolean execute(String s, int i) {
          if (i >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            result.add(factory.createImportStatementFromText(s, false, true, null));
            final PsiPackage aPackage = JavaPsiFacade.getInstance(myFile.getProject()).findPackage(s);
            if (aPackage != null) {
              for (PsiClass clazz : aPackage.getClasses(myFile.getResolveScope())) {
                onDemandImportedSimpleClassNames.add(clazz.getName());
              }
            }
          }
          return true;
        }
      });

      classCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        public boolean execute(String s, int i) {
          if (i >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            result.add(factory.createImportStatementFromText(s, true, true, null));
          }
          return true;
        }
      });

      List<GrImportStatement> explicated = CollectionFactory.arrayList();
      for (String importedClass : importedClasses) {
        final String parentName = StringUtil.getPackageName(importedClass);
        if (packageCountMap.get(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        if (implicitlyImported.contains(importedClass) &&
            !onDemandImportedSimpleClassNames.contains(StringUtil.getShortName(importedClass))) {
          continue;
        }

        explicated.add(factory.createImportStatementFromText(importedClass, false, false, null));
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = StringUtil.getPackageName(importedMember);
        if (classCountMap.get(className) >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        result.add(factory.createImportStatementFromText(importedMember, true, false, null));
      }

      final Comparator<GrImportStatement> comparator = new Comparator<GrImportStatement>() {
        public int compare(GrImportStatement statement1, GrImportStatement statement2) {
          if (settings.LAYOUT_STATIC_IMPORTS_SEPARATELY) {
            if (statement1.isStatic() && !statement2.isStatic()) return 1;
            if (statement2.isStatic() && !statement1.isStatic()) return -1;
          }

          if (!statement1.isStatic() && !statement2.isStatic()) {
            if (isImportToJavaOrJavax(statement1) && !isImportToJavaOrJavax(statement2)) return 1;
            if (!isImportToJavaOrJavax(statement1) && isImportToJavaOrJavax(statement2)) return -1;
          }


          final GrCodeReferenceElement ref1 = statement1.getImportReference();
          final GrCodeReferenceElement ref2 = statement2.getImportReference();
          String name1 = ref1 != null ? PsiUtil.getQualifiedReferenceText(ref1) : null;
          String name2 = ref2 != null ? PsiUtil.getQualifiedReferenceText(ref2) : null;
          if (name1 == null) return name2 == null ? 0 : -1;
          if (name2 == null) return 1;
          return name1.compareTo(name2);
        }
      };
      Collections.sort(result, comparator);
      Collections.sort(explicated, comparator);

      explicated.addAll(result);
      return explicated.toArray(new GrImportStatement[explicated.size()]);
    }
  }

  public static List<GrImportStatement> getValidImportStatements(final GroovyFile file) {
    final List<GrImportStatement> oldImports = new ArrayList<GrImportStatement>();
    for (GrImportStatement statement : file.getImportStatements()) {
      final GrCodeReferenceElement reference = statement.getImportReference();
      if (reference != null && reference.multiResolve(false).length > 0) {
        oldImports.add(statement);
      }
    }
    return oldImports;
  }
}
