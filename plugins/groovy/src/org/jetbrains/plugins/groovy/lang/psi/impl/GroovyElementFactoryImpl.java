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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author ven
 */
public class GroovyElementFactoryImpl extends GroovyElementFactory implements ProjectComponent {
  Project myProject;

  public GroovyElementFactoryImpl(Project project){
    myProject = project;
  }

  private static String DUMMY = "dummy.";

  public PsiElement createIdentifierFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return ((GrReferenceExpression) ((GroovyFile) file).getTopStatements()[0]).getReferenceNameElement();
  }

  @Nullable
  public GroovyPsiElement createTopElementFromText(String text) {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
            text);
    return (GroovyPsiElement) dummyFile.getFirstChild();
  }

  public GroovyPsiElement createClosureFromText(String s) {
    PsiFile psiFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), s);
    return (GroovyPsiElement) psiFile.getFirstChild();
  }

  private PsiFile createGroovyFile(String idText) {
    return PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), idText);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Element Factory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
            " ");
    return dummyFile.getFirstChild();
  }

  public GrImportStatement createImportStatementFromText(String qName) {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
            "import " + qName + " ");
    return ((GrImportStatement) dummyFile.getFirstChild());
  }


}
