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
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import java.util.*;

/**
 * @author ilyas
 */
public class GroovyInspectionData implements ProjectComponent {

  private Project myProject;
  private Map<GroovyFile, Map<GrImportStatement, ImportInfo>> myUsedImportStatements = new HashMap<GroovyFile, Map<GrImportStatement, ImportInfo>>();
  private ImportInfoListener myListener;

  public GroovyInspectionData(Project project) {
    myProject = project;
    myListener = new ImportInfoListener();
  }

  public void updateImportUsed(GrImportStatement importStatement, GrReferenceElement element) {
    if (importStatement.getParent() == null) {
      return;
    }
    PsiFile file = importStatement.getContainingFile();
    if (file == null || !(file instanceof GroovyFile)) return;
    GroovyFile groovyFile = (GroovyFile) file;
    // Used import info for current file
    Map<GrImportStatement, ImportInfo> importInfos = myUsedImportStatements.get(groovyFile);
    if (importInfos == null) {
      Map<GrImportStatement, ImportInfo> importInfo = new HashMap<GrImportStatement, ImportInfo>();
      importInfo.put(importStatement, new ImportInfo(importStatement, element));
      myUsedImportStatements.put(groovyFile, importInfo);
    } else {
      GroovyInspectionData.ImportInfo importInfo = importInfos.get(importStatement);
      if (importInfo == null) {
        importInfos.put(importStatement, new ImportInfo(importStatement, element));
      } else {
        importInfo.addReferenceElement(element);
      }
    }
  }

  @NotNull
  public GrImportStatement[] getUsedImportStatements(GroovyFile file) {
    validateImportsInFile(file);
    Map<GrImportStatement, ImportInfo> importInfos = myUsedImportStatements.get(file);
    if (importInfos == null || importInfos.size() == 0) {
      return GrImportStatement.EMPTY_ARRAY;
    } else {
      return importInfos.keySet().toArray(new GrImportStatement[importInfos.keySet().size()]);
    }
  }

  public void validateImportsInFile(GroovyFile file) {
    Map<GrImportStatement, ImportInfo> importInfos = myUsedImportStatements.get(file);
    Iterator<Map.Entry<GrImportStatement, ImportInfo>> iterator = importInfos.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<GrImportStatement, ImportInfo> entry = iterator.next();
      if (!entry.getKey().isValid() || !entry.getValue().isActual()) {
        iterator.remove();
      }
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
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(myListener);

  }

  public void disposeComponent() {
    PsiManager.getInstance(myProject).removePsiTreeChangeListener(myListener);
  }

  public static GroovyInspectionData getInstance(Project project) {
    return project.getComponent(GroovyInspectionData.class);
  }


  private class ImportInfo {

    private Set<GrReferenceElement> myReferenceElements = new HashSet<GrReferenceElement>();
    private GrImportStatement myImportStatement;

    public ImportInfo(GrImportStatement importStatement, GrReferenceElement element) {
      myReferenceElements.add(element);
      myImportStatement = importStatement;
    }

    public void addReferenceElement(GrReferenceElement element) {
      myReferenceElements.add(element);
    }

    public boolean isActual() {
      Iterator<GrReferenceElement> iterator = myReferenceElements.iterator();
      while (iterator.hasNext()) {
        GrReferenceElement element = iterator.next();
        if (!element.isValid()) {
          iterator.remove();
          continue;
        }
        GroovyResolveResult resolveResult = element.advancedResolve();
        if (!myImportStatement.equals(resolveResult.getImportStatementContext())) {
          iterator.remove();
        }
      }
      return !myReferenceElements.isEmpty();
    }
  }

  private class ImportInfoListener extends PsiTreeChangeAdapter {
    private void updateImportInfo(PsiTreeChangeEvent event) {
      if (event.getFile() instanceof GroovyFile) {
        GroovyFile file = (GroovyFile) event.getFile();
        validateImportsInFile(file);
      }
    }

    public void childAdded(PsiTreeChangeEvent event) {
      updateImportInfo(event);
    }

    public void childRemoved(PsiTreeChangeEvent event) {
      updateImportInfo(event);
    }

    public void childReplaced(PsiTreeChangeEvent event) {
      updateImportInfo(event);
    }

    public void childrenChanged(PsiTreeChangeEvent event) {
      updateImportInfo(event);
    }

    public void childMoved(PsiTreeChangeEvent event) {
      updateImportInfo(event);
    }

  }
}
