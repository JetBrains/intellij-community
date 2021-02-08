// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

public final class GroovyImportOptimizer implements ImportOptimizer {
  public static Comparator<GrImportStatement> getComparator(final GroovyCodeStyleSettings settings) {
    return (statement1, statement2) -> {
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
  @NotNull
  public Runnable processFile(@NotNull PsiFile file) {
    return new MyProcessor((GroovyFile)file).compute();
  }

  private static final class MyProcessor implements NotNullComputable<Runnable> {
    private final GroovyFile myFile;

    private MyProcessor(@NotNull GroovyFile file) {
      myFile = file;
    }

    @NotNull
    @Override
    public Runnable compute() {
      final Set<String> simplyImportedClasses = new LinkedHashSet<>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<>();
      final Set<GrImportStatement> usedImports = new HashSet<>();
      final Set<GrImportStatement> unresolvedOnDemandImports = new HashSet<>();
      final Set<String> implicitlyImportedClasses = new LinkedHashSet<>();
      final Set<String> innerClasses = new HashSet<>();
      final Map<String, String> aliasImported = new HashMap<>();
      final Map<String, String> annotatedImports = new HashMap<>();

      GroovyImportUtil.processFile(myFile, simplyImportedClasses, staticallyImportedMembers, usedImports, unresolvedOnDemandImports,
                                   implicitlyImportedClasses, innerClasses,
                                   aliasImported, annotatedImports);
      final List<GrImportStatement> oldImports = PsiUtil.getValidImportStatements(myFile);

      // Add new import statements
      GrImportStatement[] newImports =
        prepare(usedImports, simplyImportedClasses, staticallyImportedMembers, implicitlyImportedClasses, innerClasses, aliasImported,
                annotatedImports, unresolvedOnDemandImports);
      if (oldImports.isEmpty() && newImports.length == 0 && aliasImported.isEmpty()) return EmptyRunnable.getInstance();

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myFile.getProject());

      final GroovyFile tempFile = factory.createGroovyFile("", false, null);
      tempFile.putUserData(PsiFileFactory.ORIGINAL_FILE, myFile);

      for (GrImportStatement newImport : newImports) {
        tempFile.addImport(newImport);
      }

      if (!oldImports.isEmpty()) {
        final int startOffset = oldImports.get(0).getTextRange().getStartOffset();
        final int endOffset = oldImports.get(oldImports.size() - 1).getTextRange().getEndOffset();
        String oldText = myFile.getText().substring(startOffset, endOffset);
        if (tempFile.getText().trim().equals(oldText)) return EmptyRunnable.getInstance();
      }
      return () -> {
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myFile.getProject());
        final Document document = documentManager.getDocument(myFile);
        if (document != null) documentManager.commitDocument(document);

        List<GrImportStatement> existingImports = PsiUtil.getValidImportStatements(myFile);

        for (GrImportStatement statement : tempFile.getImportStatements()) {
          myFile.addImport(statement);
        }

        for (GrImportStatement importStatement : existingImports) {
          myFile.removeImport(importStatement);
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
      final GroovyCodeStyleSettings settings = GroovyCodeStyleSettings.getInstance(myFile);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      Object2IntMap<String> packageCountMap=new Object2IntOpenHashMap<>();
      Object2IntMap<String> classCountMap=new Object2IntOpenHashMap<>();

      //init packageCountMap
      for (String importedClass : importedClasses) {
        if (implicitlyImported.contains(importedClass) ||
            innerClasses.contains(importedClass) ||
            aliased.containsKey(importedClass) ||
            annotations.containsKey(importedClass)) {
          continue;
        }

        final String packageName = StringUtil.getPackageName(importedClass);

        packageCountMap.mergeInt(packageName, 1, Math::addExact);
      }

      //init classCountMap
      for (String importedMember : staticallyImportedMembers) {
        if (aliased.containsKey(importedMember) || annotations.containsKey(importedMember)) {
          continue;
        }

        classCountMap.mergeInt(StringUtil.getPackageName(importedMember), 1, Math::addExact);
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<>();
      final List<GrImportStatement> result = new ArrayList<>();

      for (Object2IntMap.Entry<String> entry : packageCountMap.object2IntEntrySet()) {
        String s = entry.getKey();
        if (entry.getIntValue() >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND || settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(s)) {
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
      }

      for (Object2IntMap.Entry<String> entry : classCountMap.object2IntEntrySet()) {
        if (entry.getIntValue() >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
          final GrImportStatement imp = factory.createImportStatementFromText(entry.getKey(), true, true, null);
          String annos = annotations.remove(entry.getKey() + ".*");
          if (annos != null) {
            imp.getAnnotationList().replace(factory.createModifierList(annos));
          }
          result.add(imp);
        }
      }

      List<GrImportStatement> explicated = new ArrayList<>();
      for (String importedClass : importedClasses) {
        final String parentName = StringUtil.getPackageName(importedClass);
        if (!annotations.containsKey(importedClass) && !aliased.containsKey(importedClass)) {
          if (packageCountMap.getInt(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND ||
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

      explicated.addAll(unresolvedOnDemandImports);

      return explicated.toArray(GrImportStatement.EMPTY_ARRAY);
    }
  }
}
