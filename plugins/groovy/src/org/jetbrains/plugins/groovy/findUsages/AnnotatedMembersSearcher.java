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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedMembersSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyCacheUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class AnnotatedMembersSearcher implements QueryExecutor<PsiMember, AnnotatedMembersSearch.Parameters> {

  public boolean execute(final AnnotatedMembersSearch.Parameters p, final Processor<PsiMember> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    final SearchScope scope = p.getScope();

    GrMember[] candidates;
    if (scope instanceof GlobalSearchScope) {
      candidates = GroovyCacheUtil.getAnnotatedMemberCandidates(annClass, ((GlobalSearchScope)scope));
    } else {
      PsiElement[] elements = ((LocalSearchScope) scope).getScope();
      final List<GrMember> collector = new ArrayList<GrMember>();
      for (PsiElement element : elements) {
        if (element instanceof GroovyPsiElement) {
          ((GroovyPsiElement) element).accept(new GroovyRecursiveElementVisitor() {
            public void visitMethod(GrMethod method) {
              collector.add(method);
            }

            public void visitField(GrField field) {
              collector.add(field);
            }
          });
        }
      }
      candidates = collector.toArray(new GrMember[collector.size()]);
    }

    for (GrMember candidate : candidates) {
      GrModifierList list = candidate.getModifierList();
      if (list != null) {
        GrAnnotation[] annotations = list.getAnnotations();
        for (GrAnnotation annotation : annotations) {
          GrCodeReferenceElement ref = annotation.getClassReference();
          if (ref != null && ref.isReferenceTo(annClass)) {
            if (!consumer.process(candidate)) return false;
          }
        }
      }
    }

    return true;
  }


}
