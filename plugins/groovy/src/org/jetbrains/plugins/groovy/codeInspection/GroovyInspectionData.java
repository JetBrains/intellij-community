package org.jetbrains.plugins.groovy.codeInspection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intellij.psi.PsiFile;

/**
 * @author ilyas
 */
public class GroovyInspectionData {

  private static final GroovyInspectionData INSTANCE = new GroovyInspectionData();

  private Map<GroovyFile, Set<GrImportStatement>> myUsedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();

  public void registerImportUsed(GrImportStatement importStatement) {
    PsiFile file = importStatement.getContainingFile();
    if (file == null || !(file instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) file;
    Set<GrImportStatement> importStatements = myUsedImportStatements.get(groovyFile);
    if (importStatements == null) {
      HashSet<GrImportStatement> statements = new HashSet<GrImportStatement>();
      statements.add(importStatement);
      myUsedImportStatements.put(groovyFile, statements);
    } else {
      importStatements.add(importStatement);
    }
  }

  public void clearImportsForFile(GroovyFile file) {
    if (file == null || myUsedImportStatements.get(file) == null) return;
    myUsedImportStatements.get(file).clear();
  }

  @NotNull
  public GrImportStatement[] getUsedImportStatements(GroovyFile file) {
    Set<GrImportStatement> importStatements = myUsedImportStatements.get(file);
    if (importStatements == null) {
      HashSet<GrImportStatement> statements = new HashSet<GrImportStatement>();
      myUsedImportStatements.put(file, statements);
      return statements.toArray(new GrImportStatement[statements.size()]);
    }
    return importStatements.toArray(new GrImportStatement[importStatements.size()]);
  }

  public static GroovyInspectionData getInstance() {
    return INSTANCE;
  }
}
