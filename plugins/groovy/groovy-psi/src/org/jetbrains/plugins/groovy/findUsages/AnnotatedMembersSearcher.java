// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class AnnotatedMembersSearcher implements QueryExecutor<PsiModifierListOwner, AnnotatedElementsSearch.Parameters> {

  private static @NotNull List<PsiModifierListOwner> getAnnotatedMemberCandidates(final PsiClass clazz, final GlobalSearchScope scope) {
    final String name = ReadAction.compute(() -> clazz.getName());
    if (name == null) return Collections.emptyList();
    final Collection<PsiElement> members = ReadAction
      .compute(() -> StubIndex.getElements(GrAnnotatedMemberIndex.KEY, name, clazz.getProject(), scope, PsiElement.class));
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
  public boolean execute(final @NotNull AnnotatedElementsSearch.Parameters p, final @NotNull Processor<? super PsiModifierListOwner> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated members search";

    final String annotationFQN = ReadAction.compute(() -> annClass.getQualifiedName());
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
              public void visitMethod(@NotNull GrMethod method) {
                candidates.add(method);
              }

              @Override
              public void visitField(@NotNull GrField field) {
                candidates.add(field);
              }
            });
          }
        });
      }
    }

    for (final PsiModifierListOwner candidate : candidates) {
      boolean accepted = ReadAction.compute(() -> {
        if (AnnotatedElementsSearcher.isInstanceof(candidate, p.getTypes())) {
          PsiModifierList list = candidate.getModifierList();
          if (list instanceof GrModifierList) {
            for (PsiAnnotation annotation : ((GrModifierList)list).getRawAnnotations()) {
              if ((p.isApproximate() || annotation.hasQualifiedName(annotationFQN)) && !consumer.process(candidate)) {
                return false;
              }
            }
          }
        }
        return true;
      });
      if (!accepted) return false;
    }

    return true;
  }


}
