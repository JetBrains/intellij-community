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
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrPropertyForCompletion;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GrKindWeigher extends CompletionWeigher {
  private static final Set<String> TRASH_CLASSES = new HashSet<String>(10);
  static {
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_CLASS);
    TRASH_CLASSES.add(CommonClassNames.JAVA_LANG_OBJECT);
    TRASH_CLASSES.add(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT);
  }

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }

    final PsiElement position = location.getCompletionParameters().getPosition();
    if (!(position.getParent() instanceof GrReferenceElement)) {
      if (o instanceof PsiClass || o instanceof PsiPackage) return 0;
      return 1;
    }

    if (!(o instanceof PsiElement)) return null;

    final GrReferenceElement parent = (GrReferenceElement)position.getParent();

    if (parent.getQualifier() == null) {
      if (o instanceof GrVariable && GroovyRefactoringUtil.isLocalVariable((GrVariable)o)) return NotQualifiedKind.aLocal;
      if (o instanceof PsiClass) return NotQualifiedKind.aClass;
      if (o instanceof PsiPackage) return NotQualifiedKind.aPackage;
      if (isLightElement(o)) return NotQualifiedKind.anImplicitGroovyMethod;
      if (o instanceof PsiMember) return NotQualifiedKind.aMember;
    }
    else {
      if (o instanceof PsiClass) return QualifiedKind.aClass;
      if (o instanceof PsiPackage) return QualifiedKind.aPackage;
      if (isLightElement(o)) {
        return QualifiedKind.anImplicitGroovyMethod;
      }
      if (o instanceof GrEnumConstant || o instanceof PsiEnumConstant) return QualifiedKind.anEnumConstant;
      if (o instanceof PsiMember) {
        final PsiClass containingClass = ((PsiMember)o).getContainingClass();
        if (containingClass != null) {
          if (TRASH_CLASSES.contains(containingClass.getQualifiedName())) {
            return QualifiedKind.aTrashMethod;
          }
        }
        return QualifiedKind.aMember;
      }
    }
    return null;
  }

  private static boolean isLightElement(Object o) {
    return o instanceof LightElement && !(o instanceof GrPropertyForCompletion) && !(o instanceof GrAccessorMethod);
  }

  static enum NotQualifiedKind {
    aPackage, aClass, anImplicitGroovyMethod, aMember, aLocal
  }

  static enum QualifiedKind {
    aPackage, aClass, aTrashMethod, anImplicitGroovyMethod, aMember, anEnumConstant
  }
}
