package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @authopr ven
 */
public class GroovyImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer");

  @NotNull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file);
  }

  private class MyProcessor implements Runnable {
    private GroovyFile myFile;

    public MyProcessor(PsiFile file) {
      myFile = (GroovyFile) file;
    }

    public void run() {
      final Set<String> importedClasses = new LinkedHashSet<String>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<String>();
      final Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
      myFile.accept(new GroovyRecursiveElementVisitor() {
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          visitRefElement(refElement);
          super.visitCodeReferenceElement(refElement);
        }

        public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
          visitRefElement(referenceExpression);
          super.visitReferenceExpression(referenceExpression);
        }

        private void visitRefElement(GrReferenceElement refElement) {
          final GroovyResolveResult resolveResult = refElement.advancedResolve();
          final GrImportStatement importStatement = resolveResult.getImportStatementContext();
          if (importStatement != null) {
            usedImports.add(importStatement);
            if (!importStatement.isAliasedImport()) {
              String importedName = null;
              if (importStatement.isOnDemand()) {
                final PsiElement element = resolveResult.getElement();
                if (element == null) return;

                if (importStatement.isStatic()) {
                  if (element instanceof PsiMember) {
                    final PsiMember member = (PsiMember) element;
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
                } else {
                  if (element instanceof PsiClass) {
                    importedName = ((PsiClass) element).getQualifiedName();
                  } else if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
                    importedName = ((PsiMethod) element).getContainingClass().getQualifiedName();
                  }
                }
              } else {
                final GrCodeReferenceElement importReference = importStatement.getImportReference();
                if (importReference != null) {
                  importedName = PsiUtil.getQualifiedReferenceText(importReference);
                }
              }

              if (importedName == null) return;

              if (importStatement.isStatic()) {
                staticallyImportedMembers.add(importedName);
              } else {
                importedClasses.add(importedName);
              }
            }
          }
        }
      });

      for (GrImportStatement importStatement : myFile.getImportStatements()) {
        try {
          if (!importStatement.isAliasedImport() || !usedImports.contains(importStatement)) {
            myFile.removeImport(importStatement);
          }
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      GrImportStatement[] newImports = prepare(importedClasses, staticallyImportedMembers);
      try {
        for (GrImportStatement newImport : newImports) {
          myFile.addImport(newImport);
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    private GrImportStatement[] prepare(Set<String> importedClasses, Set<String> staticallyImportedMembers) {
      final Project project = myFile.getProject();
      final PsiManager manager = myFile.getManager();
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
      final GroovyElementFactory factory = GroovyElementFactory.getInstance(project);

      TObjectIntHashMap<String> packageCountMap = new TObjectIntHashMap<String>();
      TObjectIntHashMap<String> classCountMap = new TObjectIntHashMap<String>();

      for (String importedClass : importedClasses) {
        final String packageName = getParentName(importedClass);

        if (isPackageImplicitlyImported(packageName)) continue;
        if (!packageCountMap.containsKey(packageName)) packageCountMap.put(packageName, 0);
        packageCountMap.increment(packageName);
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = getParentName(importedMember);
        if (!classCountMap.containsKey(className)) packageCountMap.put(className, 0);
        classCountMap.increment(className);
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<String>();
      final List<GrImportStatement> result = new ArrayList<GrImportStatement>();
      packageCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        public boolean execute(String s, int i) {
          if (i >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            result.add(factory.createImportStatementFromText(s, false, true));
            final PsiPackage aPackage = manager.findPackage(s);
            if (aPackage != null) {
              for (PsiClass clazz : aPackage.getClasses()) {
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
            result.add(factory.createImportStatementFromText(s, true, true));
          }
          return true;
        }
      });

      for (String importedClass : importedClasses) {
        final String parentName = getParentName(importedClass);
        if (packageCountMap.get(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        if (isClassImplicitlyImported(importedClass) && !onDemandImportedSimpleClassNames.contains(getSimpleName(importedClass)))
          continue;

        result.add(factory.createImportStatementFromText(importedClass, false, false));
      }

      for (String importedMember : staticallyImportedMembers) {
        final String parentName = getParentName(importedMember);
        if (classCountMap.get(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        result.add(factory.createImportStatementFromText(importedMember, true, false));
      }

      GrImportStatement[] statements = result.toArray(new GrImportStatement[result.size()]);
      Arrays.sort(statements, new Comparator<GrImportStatement>() {
        public int compare(GrImportStatement statement1, GrImportStatement statement2) {
          final GrCodeReferenceElement ref1 = statement1.getImportReference();
          final GrCodeReferenceElement ref2 = statement2.getImportReference();
          String name1 = ref1 != null ? PsiUtil.getQualifiedReferenceText(ref1) : null;
          String name2 = ref2 != null ? PsiUtil.getQualifiedReferenceText(ref2) : null;
          if (name1 == null) return name2 == null ? 0 : -1;
          if (name2 == null) return 1;
          return name1.compareTo(name2);
        }
      });
      return statements;
    }

    private boolean isClassImplicitlyImported(String importedClass) {
      for (String implicitlyImportedClass : GroovyFile.IMPLICITLY_IMPORTED_CLASSES) {
        if (importedClass.equals(implicitlyImportedClass)) return true;
      }

      return isPackageImplicitlyImported(getParentName(importedClass));
    }

    private boolean isPackageImplicitlyImported(String packageName) {
      for (String implicitlyImportedPackage : GroovyFile.IMPLICITLY_IMPORTED_PACKAGES) {
        if (packageName.equals(implicitlyImportedPackage)) return true;
      }
      return false;
    }

    private String getParentName(String qname) {
      final int dot = qname.lastIndexOf('.');
      return dot > 0 ? qname.substring(0, dot) : "";
    }

    private String getSimpleName(String qname) {
      final int dot = qname.lastIndexOf('.');
      return dot > 0 ? qname.substring(dot) : qname;
    }
  }
}
