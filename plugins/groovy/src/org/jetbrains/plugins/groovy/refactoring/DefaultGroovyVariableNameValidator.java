/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class DefaultGroovyVariableNameValidator implements NameValidator {
  private final PsiElement myContext;
  private final Set<String> mySet = new HashSet<String>();

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context) {
    this(context, Collections.<String>emptyList());
  }

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context, Collection<String> restrictedNames) {
    this(context, restrictedNames, true);
  }

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context, Collection<String> restrictedNames, boolean includeFields) {
    myContext = context;
    mySet.addAll(restrictedNames);
    PropertyResolverProcessor processor = new PropertyResolverProcessor(null, myContext);
    ResolveUtil.treeWalkUp(myContext, processor, true);
    final GroovyResolveResult[] results = processor.getCandidates();
    for (GroovyResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiNamedElement && (includeFields || !(element instanceof PsiField))) {
        mySet.add(((PsiNamedElement)element).getName());
      }
    }

    context.accept(new GroovyRecursiveElementVisitor(){
      @Override
      public void visitVariable(GrVariable variable) {
        mySet.add(variable.getName());
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {}

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {}
    });
  }

  public String validateName(String name, boolean increaseNumber) {
    if (!mySet.contains(name)) return name;
    if (increaseNumber) {
      int i = 1;
      //noinspection StatementWithEmptyBody
      for (; mySet.contains(name + i); i++) ;
      return name + i;
    }
    else {
      return "";
    }
  }

  public Project getProject() {
    return myContext.getProject();
  }
}
