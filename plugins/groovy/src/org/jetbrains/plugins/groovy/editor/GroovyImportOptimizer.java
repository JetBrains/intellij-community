// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiPackage;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GroovyImportOptimizer implements ImportOptimizer {
  public static Comparator<GrImportStatement> getComparator(GroovyCodeStyleSettings settings) {
    return (statement1, statement2) -> {
      if (statement1.isModule() && !statement2.isModule()) return -1;
      if (statement2.isModule() && !statement1.isModule()) return 1;
      if (settings.LAYOUT_STATIC_IMPORTS_SEPARATELY) {
        if (statement1.isStatic() && !statement2.isStatic()) return 1;
        if (statement2.isStatic() && !statement1.isStatic()) return -1;
      }

      String name1 = statement1.getImportFqn();
      String name2 = statement2.getImportFqn();
      if (name1 == null) return name2 == null ? 0 : -1;
      if (name2 == null) return 1;
      return name1.compareTo(name2);
    };
  }

  @Override
  public boolean supports(@NotNull PsiFile file) {
    return file instanceof GroovyFile;
  }

  @Override
  public @NotNull Runnable processFile(@NotNull PsiFile file) {
    return new MyProcessor((GroovyFile)file).compute();
  }

  private static final class MyProcessor implements Computable<@NotNull Runnable> {
    private final GroovyFile myFile;

    private MyProcessor(@NotNull GroovyFile file) {
      myFile = file;
    }

    @Override
    public @NotNull Runnable compute() {
      final Set<String> importedModules = new LinkedHashSet<>();
      final Set<String> simplyImportedClasses = new LinkedHashSet<>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<>();
      final Set<GrImportStatement> usedImports = new HashSet<>();
      final Set<GrImportStatement> unresolvedOnDemandImports = new HashSet<>();
      final Set<String> implicitlyImportedClasses = new LinkedHashSet<>();
      final Set<String> innerClasses = new HashSet<>();
      final Map<String, String> aliasImported = new HashMap<>();
      final Map<String, String> annotatedImports = new HashMap<>();

      GroovyImportUtil.processFile(myFile, importedModules, simplyImportedClasses, staticallyImportedMembers, usedImports,
                                   unresolvedOnDemandImports, implicitlyImportedClasses, innerClasses, aliasImported, annotatedImports);
      final List<GrImportStatement> oldImports = PsiUtil.getValidImportStatements(myFile);

      // Add new import statements
      final GrImportStatement[] newImports =
        prepare(usedImports, importedModules, simplyImportedClasses, staticallyImportedMembers, implicitlyImportedClasses, innerClasses, 
                aliasImported, annotatedImports, unresolvedOnDemandImports);
      if (oldImports.isEmpty() && newImports.length == 0 && aliasImported.isEmpty()) return EmptyRunnable.getInstance();

      final GroovyFile tempFile = GroovyPsiElementFactory.getInstance(myFile.getProject()).createGroovyFile("", false, null);
      tempFile.putUserData(PsiFileFactory.ORIGINAL_FILE, myFile);

      for (GrImportStatement newImport : newImports) {
        tempFile.addImport(newImport);
      }

      if (!oldImports.isEmpty()) {
        final int startOffset = oldImports.getFirst().getTextRange().getStartOffset();
        final int endOffset = oldImports.getLast().getTextRange().getEndOffset();
        final String oldText = myFile.getText().substring(startOffset, endOffset);
        if (tempFile.getText().trim().equals(oldText)) return EmptyRunnable.getInstance();
      }
      return () -> {
        PsiDocumentManager.getInstance(myFile.getProject()).commitDocument(myFile.getFileDocument());
        final List<GrImportStatement> existingImports = PsiUtil.getValidImportStatements(myFile);

        for (GrImportStatement statement : tempFile.getImportStatements()) {
          myFile.addImport(statement);
        }

        for (GrImportStatement importStatement : existingImports) {
          myFile.removeImport(importStatement);
        }
      };
    }

    private GrImportStatement[] prepare(Set<GrImportStatement> usedImports,
                                        Set<String> importedModules,
                                        Set<String> importedClasses,
                                        Set<String> staticallyImportedMembers,
                                        Set<String> implicitlyImported,
                                        Set<String> innerClasses,
                                        Map<String, String> aliased,
                                        Map<String, String> annotations,
                                        Set<GrImportStatement> unresolvedOnDemandImports) {
      final Project project = myFile.getProject();
      final GroovyCodeStyleSettings settings = GroovyCodeStyleSettings.getInstance(myFile);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      Object2IntMap<String> packageCountMap = new Object2IntOpenHashMap<>();
      for (String importedClass : importedClasses) {
        if (implicitlyImported.contains(importedClass) ||
            innerClasses.contains(importedClass) ||
            aliased.containsKey(importedClass) ||
            annotations.containsKey(importedClass)) {
          continue;
        }

        packageCountMap.mergeInt(StringUtil.getPackageName(importedClass), 1, Math::addExact);
      }

      final Object2IntMap<String> classCountMap = new Object2IntOpenHashMap<>();
      for (String importedMember : staticallyImportedMembers) {
        if (aliased.containsKey(importedMember) || annotations.containsKey(importedMember)) {
          continue;
        }

        classCountMap.mergeInt(StringUtil.getPackageName(importedMember), 1, Math::addExact);
      }

      final List<GrImportStatement> result = new ArrayList<>();
      for (String module : importedModules) {
        result.add(factory.createImportStatementFromText("import module " + module));
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<>();
      for (Object2IntMap.Entry<String> entry : packageCountMap.object2IntEntrySet()) {
        final String packageName = entry.getKey();
        if (entry.getIntValue() >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND
            || settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
          final GrImportStatement statement = factory.createImportStatementFromText(packageName, false, true, null);
          final String annos = annotations.remove(packageName + ".*");
          if (annos != null) {
            statement.getAnnotationList().replace(factory.createModifierList(annos));
          }
          result.add(statement);
          final PsiPackage aPackage = JavaPsiFacade.getInstance(myFile.getProject()).findPackage(packageName);
          if (aPackage != null) {
            for (PsiClass clazz : aPackage.getClasses(myFile.getResolveScope())) {
              onDemandImportedSimpleClassNames.add(clazz.getName());
            }
          }
        }
      }

      final List<GrImportStatement> explicated = new ArrayList<>();
      for (String importedClass : importedClasses) {
        final String packageName = StringUtil.getPackageName(importedClass);
        if (!annotations.containsKey(importedClass) && !aliased.containsKey(importedClass)) {
          if (packageCountMap.getInt(packageName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND ||
              settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(packageName)) {
            continue;
          }
          if (implicitlyImported.contains(importedClass) &&
              !onDemandImportedSimpleClassNames.contains(StringUtil.getShortName(importedClass))) {
            continue;
          }
        }

        final GrImportStatement imp = factory.createImportStatementFromText(importedClass, false, false, null);
        final String annos = annotations.remove(importedClass);
        if (annos != null) {
          imp.getAnnotationList().replace(factory.createModifierList(annos));
        }
        explicated.add(imp);
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = StringUtil.getPackageName(importedMember);
        if (!annotations.containsKey(importedMember) && !aliased.containsKey(importedMember)) {
          if (classCountMap.getInt(className) >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        }
        result.add(factory.createImportStatementFromText(importedMember, true, false, null));
      }

      for (GrImportStatement anImport : usedImports) {
        if (anImport.isAliasedImport() || GroovyImportUtil.isAnnotatedImport(anImport)) {
          if (GroovyImportUtil.isAnnotatedImport(anImport)) {
            annotations.remove(anImport.getImportFqn());
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
      result.sort(comparator);
      explicated.sort(comparator);

      explicated.addAll(result);

      if (!annotations.isEmpty()) {
        final StringBuilder allSkippedAnnotations = new StringBuilder();
        for (String anno : annotations.values()) {
          allSkippedAnnotations.append(anno).append(' ');
        }
        if (explicated.isEmpty()) {
          explicated.add(factory.createImportStatementFromText(CommonClassNames.JAVA_LANG_OBJECT, false, false, null));
        }

        final GrImportStatement first = explicated.getFirst();

        allSkippedAnnotations.append(first.getAnnotationList().getText());
        first.getAnnotationList().replace(factory.createModifierList(allSkippedAnnotations));
      }

      explicated.addAll(unresolvedOnDemandImports);

      return explicated.toArray(GrImportStatement.EMPTY_ARRAY);
    }
  }
}
