/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.AnnotatedElementsSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class AnnotatedMembersSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {

  @NotNull
  private static List<PsiModifierListOwner> getAnnotatedMemberCandidates(final PsiClass clazz, final GlobalSearchScope scope) {
    final String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return clazz.getName();
      }
    });
    if (name == null) return Collections.emptyList();
    final Collection<PsiElement> members = ApplicationManager.getApplication().runReadAction(new Computable<Collection<PsiElement>>() {
      @Override
      public Collection<PsiElement> compute() {
        return StubIndex.getElements(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope, PsiElement.class);
      }
    });
    if (members.isEmpty()) {
      return Collections.emptyList();
    }

    final List<PsiModifierListOwner> result = new ArrayList<>();
    for (final PsiElement element : members) {
      ApplicationManager.getApplication().runReadAction(() -> {
        PsiElement e =
          element instanceof GroovyFile ?
          ((GroovyFile)element).getPackageDefinition() : element;

        if (e instanceof PsiModifierListOwner) {
          result.add((PsiModifierListOwner)e);
        }
      });
    }
    return result;
  }

  @Override
  public boolean execute(@NotNull final AnnotatedElementsSearch.Parameters p, @NotNull final Processor<PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return annClass.getQualifiedName();
      }
    });
    assert annotationFQN != null;

    final SearchScope scope = p.getScope();

    final List<PsiModifierListOwner> candidates;
    if (scope instanceof GlobalSearchScope) {
      candidates = getAnnotatedMemberCandidates(annClass, ((GlobalSearchScope)scope));
    }
    else {
      candidates = new ArrayList<>();
      for (final PsiElement element : ((LocalSearchScope)scope).getScope()) {
        ApplicationManager.getApplication().runReadAction(() -> {
          if (element instanceof GroovyPsiElement) {
            ((GroovyPsiElement)element).accept(new GroovyRecursiveElementVisitor() {
              @Override
              public void visitMethod(GrMethod method) {
                candidates.add(method);
              }

              @Override
              public void visitField(GrField field) {
                candidates.add(field);
              }
            });
          }
        });
      }
    }

    for (final PsiModifierListOwner candidate : candidates) {
      boolean accepted = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
        @Override
        public Boolean compute() {
          if (AnnotatedElementsSearcher.isInstanceof(candidate, p.getTypes())) {
            PsiModifierList list = candidate.getModifierList();
            if (list != null) {
              for (PsiAnnotation annotation : list.getAnnotations()) {
                if (annotationFQN.equals(annotation.getQualifiedName()) && !consumer.process(candidate)) {
                  return false;
                }
              }
            }
          }
          return true;
        }
      });
      if (!accepted) return false;
    }

    return true;
  }


}
