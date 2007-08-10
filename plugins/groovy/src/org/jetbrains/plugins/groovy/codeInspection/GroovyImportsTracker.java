package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ven
 */
public class GroovyImportsTracker implements ProjectComponent {

  private Map<GroovyFile, Set<GrImportStatement>> myUsedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();
  private Map<GroovyFile, Set<GrImportStatement>> myUnusedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();

  public void registerImportUsed(GrImportStatement importStatement) {
    PsiFile file = importStatement.getContainingFile();
    if (file == null || !(file instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) file;
    Set<GrImportStatement> importInfos = myUsedImportStatements.get(groovyFile);
    if (importInfos == null) {
      importInfos = new HashSet<GrImportStatement>();
    }
    importInfos.add(importStatement);
    myUsedImportStatements.put(groovyFile, importInfos);
  }

  @NotNull
  public synchronized Set<GrImportStatement> getUnusedImportStatements(GroovyFile file) {
    Set<GrImportStatement> unused = myUnusedImportStatements.get(file);
    if (unused == null) {
      Set<GrImportStatement> used = myUsedImportStatements.get(file);
      unused = new HashSet<GrImportStatement>(Arrays.asList(file.getImportStatements()));
      if (used != null && used.size() > 0) {
        unused.removeAll(used);
      }

      myUnusedImportStatements.put(file, unused);
      myUsedImportStatements.remove(file);
    }
    return unused;
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Inspection Data";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static GroovyImportsTracker getInstance(Project project) {
    return project.getComponent(GroovyImportsTracker.class);
  }

  public void markFileAnnotated(GroovyFile file) {
    myUnusedImportStatements.remove(file);
  }
}
