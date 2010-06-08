/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrDynamicImplicitMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement {
  public List<MyPair> myPairs = new ArrayList<MyPair>();
  private PsiMethod myImplicitMethod;

  public DMethodElement() {
    super(null, null, null);
  }

  public DMethodElement(Boolean isStatic, String name, String returnType, List<MyPair> pairs) {
    super(isStatic, name, returnType);

    myPairs = pairs;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public void clearCache() {
    myImplicitMethod = null;
  }

  @NotNull
  public PsiMethod getPsi(PsiManager manager, final String containingClassName) {
    if (myImplicitMethod != null) return myImplicitMethod;

    final String type = getType();

    String staticModifier = null;
    Boolean isStatic = isStatic();

    if (isStatic != null && isStatic.booleanValue()) {
      staticModifier = PsiModifier.STATIC;
    }

    final String[] argumentsTypes = QuickfixUtil.getArgumentsTypes(myPairs);
    final GrMethod method = GroovyPsiElementFactory.getInstance(manager.getProject())
        .createMethodFromText(staticModifier, getName(), type, argumentsTypes, null);

    myImplicitMethod = new GrDynamicImplicitMethod(manager, method, containingClassName) {
      @Override
      public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        DynamicManager.getInstance(getProject()).replaceDynamicMethodName(containingClassName, getName(), name, argumentsTypes);
        return super.setName(name);
      }
    };
    return myImplicitMethod;
  }
}