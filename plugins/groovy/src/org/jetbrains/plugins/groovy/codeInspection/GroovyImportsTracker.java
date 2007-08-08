/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author ven
 */
public class GroovyImportsTracker implements ProjectComponent {

  private Map<GroovyFile, Set<GrImportStatement>> myUsedImportStatements = new HashMap<GroovyFile, Set<GrImportStatement>>();

  public void registerImportUsed(GrImportStatement importStatement) {
    PsiFile file = importStatement.getContainingFile();
    if (file == null || !(file instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) file;
    // Used import info for current file
    Set<GrImportStatement> importInfos = myUsedImportStatements.get(groovyFile);
    if (importInfos == null) {
      importInfos = new HashSet<GrImportStatement>();
    }
    importInfos.add(importStatement);
    myUsedImportStatements.put(groovyFile, importInfos);
  }

  @NotNull
  public GrImportStatement[] getUsedImportStatements(GroovyFile file) {
    Set<GrImportStatement> importInfos = myUsedImportStatements.get(file);
    if (importInfos == null || importInfos.size() == 0) {
      return GrImportStatement.EMPTY_ARRAY;
    } else {
      return importInfos.toArray(new GrImportStatement[importInfos.size()]);
    }
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

  public void clearImportsInFile(GroovyFile file) {
    myUsedImportStatements.put(file, null);
  }

  public boolean isImportInformationUpToDate(GroovyFile file) {
    return myUsedImportStatements.containsKey(file) &&
        myUsedImportStatements.get(file) == null;
  }

  public void markFileAnnotated(GroovyFile file) {
    if (myUsedImportStatements.containsKey(file) && myUsedImportStatements.get(file) == null) {
      myUsedImportStatements.put(file, new HashSet<GrImportStatement>());
    }
  }
}
