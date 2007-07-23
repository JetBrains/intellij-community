package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;

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
      final ArrayList<GrImportStatement> importStatements = new ArrayList<GrImportStatement>(Arrays.asList(myFile.getImportStatements()));
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
          if (importStatement != null && !isUnneeded(importStatement)) {
            importStatements.remove(importStatement);
          }
        }

        private boolean isUnneeded(GrImportStatement importStatement) {
          if (!importStatement.isOnDemand()) return false;

          final GrCodeReferenceElement ref = importStatement.getImportReference();
          if (ref != null) {
            String qName = PsiUtil.getQualifiedReferenceText(ref);
            LOG.assertTrue(qName != null);


            for (String implicitlyImportedPackage : GroovyFile.IMPLICITLY_IMPORTED_PACKAGES) {
              if (qName.equals(implicitlyImportedPackage)) return true;
            }

            for (String implicitlyImportedClass : GroovyFile.IMPLICITLY_IMPORTED_CLASSES) {
              if (qName.equals(implicitlyImportedClass)) return true;
            }
          }

          return false;
        }
      });

      for (GrImportStatement importStatement : importStatements) {
        try {
          myFile.removeImport(importStatement);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }
}
