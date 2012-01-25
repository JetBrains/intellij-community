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
package org.jetbrains.plugins.groovy.refactoring.extract.closure;


import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import gnu.trove.TIntArrayList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelper;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterSettings;

/**
 * @author Max Medvedev
 */
public class ExtractClosureHelper extends ExtractInfoHelperBase implements ExtractInfoHelper, GrIntroduceParameterSettings {

  private final GrParametersOwner myOwner;
  private final PsiElement myToSearchFor;

  private String myName;
  private boolean myFinal;
  private TIntArrayList toRemove;
  private boolean myGenerateDelegate;

  public ExtractClosureHelper(InitialInfo info, GrParametersOwner owner, PsiElement toSearchFor, String name, boolean declareFinal) {
    super(info);
    myOwner = owner;
    myToSearchFor = toSearchFor;
    myName = name;
    myFinal = declareFinal;
  }

  public GrParametersOwner getOwner() {
    return myOwner;
  }

  public PsiElement getToSearchFor() {
    return myToSearchFor;
  }

  public String getName() {
    return myName;
  }

  public boolean declareFinal() {
    return myFinal;
  }

  public void setName(String name) {
    myName = name;
  }

  public void setDeclareFinal(boolean aFinal) {
    myFinal = aFinal;
  }

  public void setToRemove(TIntArrayList toRemove) {
    this.toRemove = toRemove;
  }

  @Override
  public TIntArrayList parametersToRemove() {
    return toRemove;
  }

  @Override
  public int replaceFieldsWithGetters() {
    return 0;//todo
  }

  @Override
  public boolean removeLocalVariable() {
    return false;
  }

  @Override
  public boolean replaceAllOccurrences() {
    return false;
  }

  @Override
  public PsiType getSelectedType() {
    return JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, myOwner);
  }

  public boolean generateDelegate() {
    return myGenerateDelegate;
  }

  public void setGenerateDelegate(boolean generateDelegate) {
    myGenerateDelegate = generateDelegate;
  }
}
