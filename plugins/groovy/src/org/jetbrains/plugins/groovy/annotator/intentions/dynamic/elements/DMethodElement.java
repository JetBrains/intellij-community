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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
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
public class DMethodElement extends DItemElement implements Comparable {
  public List<MyPair> myPairs = new ArrayList<MyPair>();
  private PsiMethod myImplicitMethod;

  public DMethodElement() {
    super(null, null);
  }

  public DMethodElement(String name, String returnType, List<MyPair> pairs) {
    super(name, returnType);

    myPairs = pairs;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public void clearCache() {
    myImplicitMethod = null;
  }

  public PsiMethod getPsi(PsiManager manager, String containingClassName) {
    if (myImplicitMethod != null) return myImplicitMethod;

    final String type = getType();

    final GrMethod method = GroovyPsiElementFactory.getInstance(manager.getProject()).createMethodFromText(getName(), type, QuickfixUtil.getArgumentsTypes(myPairs));

    myImplicitMethod = new GrDynamicImplicitMethod(manager, method, containingClassName);
    return myImplicitMethod;
  }

  public int compareTo(Object o) {
    if (!(o instanceof DMethodElement)) return 0;
    final DMethodElement otherMethod = (DMethodElement) o;

    return getName().compareTo(otherMethod.getName()) + getType().compareTo(otherMethod.getType());
  }
}