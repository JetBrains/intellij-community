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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.*;
import com.intellij.psi.impl.search.AnnotatedElementsSearcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyCacheUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class AnnotatedMembersSearcher implements QueryExecutor<PsiMember, AnnotatedElementsSearch.Parameters> {

  public boolean execute(final AnnotatedElementsSearch.Parameters p, final Processor<PsiMember> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    final SearchScope scope = p.getScope();

    PsiMember[] candidates;
    if (scope instanceof GlobalSearchScope) {
      candidates = GroovyCacheUtil.getAnnotatedMemberCandidates(annClass, ((GlobalSearchScope)scope));
    } else {
      PsiElement[] elements = ((LocalSearchScope)scope).getScope();
      final List<GrMember> collector = new ArrayList<GrMember>();
      for (PsiElement element : elements) {
        if (element instanceof GroovyPsiElement) {
          ((GroovyPsiElement)element).accept(new GroovyRecursiveElementVisitor() {
            public void visitMethod(GrMethod method) {
              collector.add(method);
            }

            public void visitField(GrField field) {
              collector.add(field);
            }
          });
        }
      }
      candidates = collector.toArray(new PsiMember[collector.size()]);
    }

    for (PsiMember candidate : candidates) {
      if (!AnnotatedElementsSearcher.isInstanceof(candidate, p.getTypes())) {
        continue;
      }

      PsiModifierList list = candidate.getModifierList();
      if (list != null) {
        for (PsiAnnotation annotation : list.getAnnotations()) {
          if (annotationFQN.equals(annotation.getQualifiedName())) {
            PsiClass clazz = candidate.getContainingClass();
            if (clazz instanceof GroovyScriptClass) continue;
            if (!consumer.process(candidate)) return false;
          }
        }
      }
    }

    return true;
  }


}
