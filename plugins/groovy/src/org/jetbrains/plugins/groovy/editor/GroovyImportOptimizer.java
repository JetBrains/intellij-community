/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.*;

import static org.jetbrains.plugins.groovy.editor.GroovyImportHelper.isImplicitlyImported;
import static org.jetbrains.plugins.groovy.editor.GroovyImportHelper.processImports;

/**
 * @author ven
 */
public class GroovyImportOptimizer implements ImportOptimizer {

  @NotNull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file, false);
  }

  public static Set<GrImportStatement> findUsedImports(GroovyFile file) {
    Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
    processFile(file, null, null, usedImports, null, null, null, null);
    return usedImports;
  }

  public boolean supports(PsiFile file) {
    return file instanceof GroovyFile;
  }

  private static void processFile(@Nullable final PsiFile file,
                                  @Nullable final Set<String> importedClasses,
                                  @Nullable final Set<String> staticallyImportedMembers,
                                  @Nullable final Set<GrImportStatement> usedImports,
                                  @Nullable final Set<String> implicitlyImported,
                                  @Nullable final Set<String> innerClasses,
                                  @Nullable final Map<String, String> aliased,
                                  @Nullable final Map<String, String> annotations) {
    if (!(file instanceof GroovyFile)) return;

    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof GrReferenceElement) {
          visitRefElement((GrReferenceElement)element);
        }
      }

      private void visitRefElement(GrReferenceElement refElement) {
        if ("super".equals(refElement.getReferenceName())) return;

        final GroovyResolveResult[] resolveResults = refElement.multiResolve(false);
        for (GroovyResolveResult resolveResult : resolveResults) {
          final PsiElement context = resolveResult.getCurrentFileResolveContext();
          final PsiElement element = resolveResult.getElement();
          if (element == null) return;

          if (context instanceof GrImportStatement) {
            final GrImportStatement importStatement = (GrImportStatement)context;

            if (usedImports != null && isImportUsed(refElement, element)) {
              usedImports.add(importStatement);
            }
            if (isImplicitlyImported(element, refElement.getReferenceName(), (GroovyFile)file)) {
              addImplicitClass(element);
            }

            if (!importStatement.isAliasedImport() && !isAnnotatedImport(importStatement)) {
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

              final String importRef = getImportReferenceText(importStatement);

              if (importStatement.isAliasedImport()) {
                if (aliased != null) {
                  aliased.put(importRef, importedName);
                }
                return;
              }

              if (importStatement.isStatic()) {
                if (staticallyImportedMembers != null) {
                  staticallyImportedMembers.add(importedName);
                }
              }
              else {
                if (importedClasses != null) {
                  importedClasses.add(importedName);
                }
                if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() != null && innerClasses != null) {
                  innerClasses.add(importedName);
                }
              }
            }
          }
          else if (context == null && !(refElement.getParent() instanceof GrImportStatement) && refElement.getQualifier() == null) {
            addImplicitClass(element);
          }
        }
      }

      private void addImplicitClass(PsiElement element) {
        final String qname = getTargetQualifiedName(element);
        if (qname != null) {
          if (implicitlyImported != null) {
            implicitlyImported.add(qname);
          }
          if (importedClasses != null) {
            importedClasses.add(qname);
          }
        }
      }

      /**
       * checks if import for implicitly imported class is needed
       */
      private boolean isImportUsed(GrReferenceElement refElement, PsiElement element) {
        if (isImplicitlyImported(element, refElement.getReferenceName(), (GroovyFile)file)) {
          final ClassResolverProcessor processor =
            new ClassResolverProcessor(refElement.getReferenceName(), refElement, ResolverProcessor.RESOLVE_KINDS_CLASS);
          processImports(ResolveState.initial(), null, refElement, processor, ((GroovyFile)file).getImportStatements(), true);
          if (!processor.hasCandidates()) {
            return false;
          }
        }
        return true;
      }
    });

    if (annotations != null) {
      ((GroovyFile)file).acceptChildren(new GroovyElementVisitor() {
        @Override
        public void visitImportStatement(GrImportStatement importStatement) {
          final String annotationText = importStatement.getAnnotationList().getText();
          if (!StringUtil.isEmptyOrSpaces(annotationText)) {
            final String importRef = getImportReferenceText(importStatement);
            annotations.put(importRef, annotationText);
          }
        }
      });
    }
  }

  @Nullable
  private static String getTargetQualifiedName(PsiElement element) {
    if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return ((PsiMethod)element).getContainingClass().getQualifiedName();
    }
    return null;
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
      final Set<String> simplyImportedClasses = new LinkedHashSet<String>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<String>();
      final Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
      final Set<String> implicitlyImportedClasses = new LinkedHashSet<String>();
      final Set<String> innerClasses = new HashSet<String>();
      Map<String, String> aliasImported = ContainerUtil.newHashMap();
      Map<String, String> annotatedImports = ContainerUtil.newHashMap();

      processFile(myFile, simplyImportedClasses, staticallyImportedMembers, usedImports, implicitlyImportedClasses, innerClasses,
                  aliasImported, annotatedImports);
      final List<GrImportStatement> oldImports = PsiUtil.getValidImportStatements(file);
      if (myRemoveUnusedOnly) {
        for (GrImportStatement oldImport : oldImports) {
          if (!usedImports.contains(oldImport)) {
            file.removeImport(oldImport);
          }
        }
        return;
      }

      // Add new import statements
      GrImportStatement[] newImports =
        prepare(usedImports, simplyImportedClasses, staticallyImportedMembers, implicitlyImportedClasses, innerClasses, aliasImported,
                annotatedImports);
      if (oldImports.isEmpty() && newImports.length == 0 && aliasImported.isEmpty()) {
        return;
      }

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());

      GroovyFile tempFile = factory.createGroovyFile("", false, null);

      for (GrImportStatement newImport : newImports) {
        tempFile.addImport(newImport);
      }

      if (oldImports.size() > 0) {
        final int startOffset = oldImports.get(0).getTextRange().getStartOffset();
        final int endOffset = oldImports.get(oldImports.size() - 1).getTextRange().getEndOffset();
        String oldText = oldImports.isEmpty() ? "" : myFile.getText().substring(startOffset, endOffset);
        if (tempFile.getText().trim().equals(oldText)) {
          return;
        }
      }

      for (GrImportStatement statement : tempFile.getImportStatements()) {
        file.addImport(statement);
      }

      for (GrImportStatement importStatement : oldImports) {
        file.removeImport(importStatement);
      }
    }

    private GrImportStatement[] prepare(final Set<GrImportStatement> usedImports,
                                        Set<String> importedClasses,
                                        Set<String> staticallyImportedMembers,
                                        Set<String> implicitlyImported,
                                        Set<String> innerClasses,
                                        Map<String, String> aliased,
                                        final Map<String, String> annotations) {
      final Project project = myFile.getProject();
      final GroovyCodeStyleSettings settings =
        CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      TObjectIntHashMap<String> packageCountMap = new TObjectIntHashMap<String>();
      TObjectIntHashMap<String> classCountMap = new TObjectIntHashMap<String>();

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

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<String>();
      final List<GrImportStatement> result = new ArrayList<GrImportStatement>();

      packageCountMap.forEachEntry(new TObjectIntProcedure<String>() {
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
        if (anImport.isAliasedImport() || isAnnotatedImport(anImport)) {
          if (isAnnotatedImport(anImport)) {
            annotations.remove(getImportReferenceText(anImport));
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

      return explicated.toArray(new GrImportStatement[explicated.size()]);
    }
  }

  private static boolean isAnnotatedImport(GrImportStatement anImport) {
    return !StringUtil.isEmptyOrSpaces(anImport.getAnnotationList().getText());
  }

  public static Comparator<GrImportStatement> getComparator(final GroovyCodeStyleSettings settings) {
    return new Comparator<GrImportStatement>() {
      public int compare(GrImportStatement statement1, GrImportStatement statement2) {
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
      }
    };
  }

  @Nullable
  private static String getImportReferenceText(GrImportStatement statement) {
    GrCodeReferenceElement importReference = statement.getImportReference();
    if (importReference != null) {
      return statement.getText().substring(importReference.getStartOffsetInParent());
    }
    return null;
  }
}
