/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 */
public class GroovyImportOptimizer implements ImportOptimizer {

  public static Comparator<GrImportStatement> getComparator(final GroovyCodeStyleSettings settings) {
    return (statement1, statement2) -> {
      if (settings.LAYOUT_STATIC_IMPORTS_SEPARATELY) {
        if (statement1.isStatic() && !statement2.isStatic()) return 1;
        if (statement2.isStatic() && !statement1.isStatic()) return -1;
      }

      final GrCodeReferenceElement ref1 = statement1.getImportReference();
      final GrCodeReferenceElement ref2 = statement2.getImportReference();
      String name1 = ref1 != null ? PsiUtil.getQualifiedReferenceText(ref1) : null;
      String name2 = ref2 != null ? PsiUtil.getQualifiedReferenceText(ref2) : null;
      if (name1 == null) return name2 == null ? 0 : -1;
      if (name2 == null) return 1;
      return name1.compareTo(name2);
    };
  }

  @Override
  @NotNull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file).compute();
  }

  @Override
  public boolean supports(PsiFile file) {
    return file instanceof GroovyFile;
  }

  private static class MyProcessor implements NotNullComputable<Runnable> {
    private final PsiFile myFile;

    private MyProcessor(PsiFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public Runnable compute() {
      if (!(myFile instanceof GroovyFile)) return EmptyRunnable.getInstance();
      final GroovyFile file = ((GroovyFile)myFile);
      final Set<String> simplyImportedClasses = new LinkedHashSet<>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<>();
      final Set<GrImportStatement> usedImports = new HashSet<>();
      final Set<GrImportStatement> unresolvedOnDemandImports = new HashSet<>();
      final Set<String> implicitlyImportedClasses = new LinkedHashSet<>();
      final Set<String> innerClasses = new HashSet<>();
      Map<String, String> aliasImported = ContainerUtil.newHashMap();
      Map<String, String> annotatedImports = ContainerUtil.newHashMap();

      GroovyImportUtil.processFile(myFile, simplyImportedClasses, staticallyImportedMembers, usedImports, unresolvedOnDemandImports,
                                   implicitlyImportedClasses, innerClasses,
                                   aliasImported, annotatedImports);
      final List<GrImportStatement> oldImports = PsiUtil.getValidImportStatements(file);

      // Add new import statements
      GrImportStatement[] newImports =
        prepare(usedImports, simplyImportedClasses, staticallyImportedMembers, implicitlyImportedClasses, innerClasses, aliasImported,
                annotatedImports, unresolvedOnDemandImports);
      if (oldImports.isEmpty() && newImports.length == 0 && aliasImported.isEmpty()) return EmptyRunnable.getInstance();

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());

      final GroovyFile tempFile = factory.createGroovyFile("", false, null);

      for (GrImportStatement newImport : newImports) {
        tempFile.addImport(newImport);
      }

      if (!oldImports.isEmpty()) {
        final int startOffset = oldImports.get(0).getTextRange().getStartOffset();
        final int endOffset = oldImports.get(oldImports.size() - 1).getTextRange().getEndOffset();
        String oldText = oldImports.isEmpty() ? "" : myFile.getText().substring(startOffset, endOffset);
        if (tempFile.getText().trim().equals(oldText)) return EmptyRunnable.getInstance();
      }
      return () -> {
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
        final Document document = documentManager.getDocument(file);
        if (document != null) documentManager.commitDocument(document);

        List<GrImportStatement> existingImports = PsiUtil.getValidImportStatements(file);

        for (GrImportStatement statement : tempFile.getImportStatements()) {
          file.addImport(statement);
        }

        for (GrImportStatement importStatement : existingImports) {
          file.removeImport(importStatement);
        }
      };
    }

    private GrImportStatement[] prepare(final Set<GrImportStatement> usedImports,
                                        Set<String> importedClasses,
                                        Set<String> staticallyImportedMembers,
                                        Set<String> implicitlyImported,
                                        Set<String> innerClasses,
                                        Map<String, String> aliased,
                                        final Map<String, String> annotations,
                                        Set<GrImportStatement> unresolvedOnDemandImports) {
      final Project project = myFile.getProject();
      final GroovyCodeStyleSettings settings =
        CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      TObjectIntHashMap<String> packageCountMap = new TObjectIntHashMap<>();
      TObjectIntHashMap<String> classCountMap = new TObjectIntHashMap<>();

      //init packageCountMap
      for (String importedClass : importedClasses) {
        if (implicitlyImported.contains(importedClass) ||
            innerClasses.contains(importedClass) ||
            aliased.containsKey(importedClass) ||
            annotations.containsKey(importedClass)) {
          continue;
        }

        final String packageName = StringUtil.getPackageName(importedClass);

        if (!packageCountMap.containsKey(packageName)) packageCountMap.put(packageName, 0);
        packageCountMap.increment(packageName);
      }

      //init classCountMap
      for (String importedMember : staticallyImportedMembers) {
        if (aliased.containsKey(importedMember) || annotations.containsKey(importedMember)) continue;

        final String className = StringUtil.getPackageName(importedMember);

        if (!classCountMap.containsKey(className)) classCountMap.put(className, 0);
        classCountMap.increment(className);
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<>();
      final List<GrImportStatement> result = new ArrayList<>();

      packageCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        @Override
        public boolean execute(String s, int i) {
          if (i >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND || settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(s)) {
            final GrImportStatement imp = factory.createImportStatementFromText(s, false, true, null);
            String annos = annotations.remove(s + ".*");
            if (annos != null) {
              imp.getAnnotationList().replace(factory.createModifierList(annos));
            }
            result.add(imp);
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
        @Override
        public boolean execute(String s, int i) {
          if (i >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            final GrImportStatement imp = factory.createImportStatementFromText(s, true, true, null);
            String annos = annotations.remove(s + ".*");
            if (annos != null) {
              imp.getAnnotationList().replace(factory.createModifierList(annos));
            }
            result.add(imp);
          }
          return true;
        }
      });

      List<GrImportStatement> explicated = ContainerUtil.newArrayList();
      for (String importedClass : importedClasses) {
        final String parentName = StringUtil.getPackageName(importedClass);
        if (!annotations.containsKey(importedClass) && !aliased.containsKey(importedClass)) {
          if (packageCountMap.get(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND ||
              settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(parentName)) {
            continue;
          }
          if (implicitlyImported.contains(importedClass) &&
              !onDemandImportedSimpleClassNames.contains(StringUtil.getShortName(importedClass))) {
            continue;
          }
        }

        final GrImportStatement imp = factory.createImportStatementFromText(importedClass, false, false, null);
        String annos = annotations.remove(importedClass);
        if (annos != null) {
          imp.getAnnotationList().replace(factory.createModifierList(annos));
        }
        explicated.add(imp);
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = StringUtil.getPackageName(importedMember);
        if (!annotations.containsKey(importedMember) && !aliased.containsKey(importedMember)) {
          if (classCountMap.get(className) >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        }
        result.add(factory.createImportStatementFromText(importedMember, true, false, null));
      }

      for (GrImportStatement anImport : usedImports) {
        if (anImport.isAliasedImport() || GroovyImportUtil.isAnnotatedImport(anImport)) {
          if (GroovyImportUtil.isAnnotatedImport(anImport)) {
            annotations.remove(GroovyImportUtil.getImportReferenceText(anImport));
          }

          if (anImport.isStatic()) {
            result.add(anImport);
          }
          else {
            explicated.add(anImport);
          }
        }
      }

      final Comparator<GrImportStatement> comparator = getComparator(settings);
      Collections.sort(result, comparator);
      Collections.sort(explicated, comparator);

      explicated.addAll(result);

      if (!annotations.isEmpty()) {
        StringBuilder allSkippedAnnotations = new StringBuilder();
        for (String anno : annotations.values()) {
          allSkippedAnnotations.append(anno).append(' ');
        }
        if (explicated.isEmpty()) {
          explicated.add(factory.createImportStatementFromText(CommonClassNames.JAVA_LANG_OBJECT, false, false, null));
        }

        final GrImportStatement first = explicated.get(0);

        allSkippedAnnotations.append(first.getAnnotationList().getText());
        first.getAnnotationList().replace(factory.createModifierList(allSkippedAnnotations));
      }

      for (GrImportStatement anImport : unresolvedOnDemandImports) {
        explicated.add(anImport);
      }

      return explicated.toArray(new GrImportStatement[explicated.size()]);
    }
  }
}
