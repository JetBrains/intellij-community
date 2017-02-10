/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.GrRefactoringConflictsUtil;
import org.jetbrains.plugins.groovy.refactoring.classMembers.GrClassMemberReferenceVisitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Max Medvedev on 9/28/13
 */
public class GrPullUpConflictsUtil {
  private GrPullUpConflictsUtil() {}

  public static MultiMap<PsiElement, String> checkConflicts(MemberInfoBase<? extends GrMember>[] infos,
                                                            PsiClass subclass,
                                                            @Nullable PsiClass superClass,
                                                            @NotNull PsiPackage targetPackage,
                                                            @NotNull PsiDirectory targetDirectory,
                                                            final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    return checkConflicts(infos, subclass, superClass, targetPackage, targetDirectory, interfaceContainmentVerifier, true);
  }

  public static MultiMap<PsiElement, String> checkConflicts(final MemberInfoBase<? extends GrMember>[] infos,
                                                            @NotNull final PsiClass subclass,
                                                            @Nullable PsiClass superClass,
                                                            @NotNull final PsiPackage targetPackage,
                                                            @NotNull PsiDirectory targetDirectory,
                                                            final InterfaceContainmentVerifier interfaceContainmentVerifier,
                                                            boolean movedMembers2Super) {
    final PsiElement targetRepresentativeElement;
    final boolean isInterfaceTarget;
    if (superClass != null) {
      isInterfaceTarget = superClass.isInterface();
      targetRepresentativeElement = superClass;
    }
    else {
      isInterfaceTarget = false;
      targetRepresentativeElement = targetDirectory;
    }

    final Set<GrMember> movedMembers = ContainerUtil.newHashSet();
    final Set<GrMethod> abstractMethods = ContainerUtil.newHashSet();
    for (MemberInfoBase<? extends GrMember> info : infos) {
      GrMember member = info.getMember();
      if (member instanceof GrMethod) {
        if (!info.isToAbstract() && !isInterfaceTarget) {
          movedMembers.add(member);
        }
        else {
          abstractMethods.add((GrMethod)member);
        }
      }
      else {
        movedMembers.add(member);
      }
    }

    final Set<PsiMethod> allAbstractMethods = new HashSet<>(abstractMethods);
    if (superClass != null) {
      for (PsiMethod method : subclass.getMethods()) {
        if (!movedMembers.contains(method) && !method.hasModifierProperty(PsiModifier.PRIVATE)) {
          if (method.findSuperMethods(superClass).length > 0) {
            allAbstractMethods.add(method);
          }
        }
      }
    }

    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();

    GrRefactoringConflictsUtil.analyzeAccessibilityConflicts(movedMembers, superClass, conflicts, VisibilityUtil.ESCALATE_VISIBILITY, targetRepresentativeElement,
                                                             allAbstractMethods);

    if (superClass != null) {
      if (movedMembers2Super) {
        checkSuperclassMembers(superClass, infos, conflicts);
        if (isInterfaceTarget) {
          checkInterfaceTarget(infos, conflicts);
        }
      } else {
        final String qualifiedName = superClass.getQualifiedName();
        assert qualifiedName != null;
        if (superClass.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
          if (!Comparing.strEqual(StringUtil.getPackageName(qualifiedName), targetPackage.getQualifiedName())) {
            conflicts.putValue(superClass, RefactoringUIUtil.getDescription(superClass, true) + " won't be accessible from " +RefactoringUIUtil.getDescription(targetPackage, true));
          }
        }
      }
    }
    // check if moved methods use other members in the classes between Subclass and Superclass
    List<PsiElement> checkModuleConflictsList = new ArrayList<>();
    for (PsiMember member : movedMembers) {
      if (member instanceof PsiMethod || member instanceof PsiClass && !(member instanceof PsiCompiledElement)) {
        GrClassMemberReferenceVisitor visitor =
          movedMembers2Super? new ConflictingUsagesOfSubClassMembers(member, movedMembers, abstractMethods, subclass, superClass,
                                                                     superClass != null ? null : targetPackage, conflicts,
                                                                     interfaceContainmentVerifier)
                            : new ConflictingUsagesOfSuperClassMembers(member, subclass, targetPackage, movedMembers, conflicts);
        ((GroovyPsiElement)member).accept(visitor);
      }
      checkModuleConflictsList.add(member);
    }
    for (final PsiMethod method : abstractMethods) {
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getParameterList());
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getReturnTypeElement());
      ContainerUtil.addIfNotNull(checkModuleConflictsList, method.getTypeParameterList());
    }
    GrRefactoringConflictsUtil.analyzeModuleConflicts(subclass.getProject(), checkModuleConflictsList, UsageInfo.EMPTY_ARRAY, targetRepresentativeElement, conflicts);
    final String fqName = subclass.getQualifiedName();
    final String packageName;
    if (fqName != null) {
      packageName = StringUtil.getPackageName(fqName);
    } else {
      final PsiFile psiFile = PsiTreeUtil.getParentOfType(subclass, PsiFile.class);
      if (psiFile instanceof PsiClassOwner) {
        packageName = ((PsiClassOwner)psiFile).getPackageName();
      } else {
        packageName = null;
      }
    }
    final boolean toDifferentPackage = !Comparing.strEqual(targetPackage.getQualifiedName(), packageName);
    for (final GrMethod abstractMethod : abstractMethods) {
      abstractMethod.accept(new GrClassMemberReferenceVisitor(subclass) {
        @Override
        protected void visitClassMemberReferenceElement(GrMember classMember, GrReferenceElement classMemberReference) {
          if (classMember != null && willBeMoved(classMember, movedMembers)) {
            boolean isAccessible = false;
            if (classMember.hasModifierProperty(PsiModifier.PRIVATE)) {
              isAccessible = true;
            }
            else if (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                     toDifferentPackage) {
              isAccessible = true;
            }
            if (isAccessible) {
              String message = RefactoringUIUtil.getDescription(abstractMethod, false) +
                               " uses " +
                               RefactoringUIUtil.getDescription(classMember, true) +
                               " which won't be accessible from the subclass.";
              message = CommonRefactoringUtil.capitalize(message);
              conflicts.putValue(classMember, message);
            }
          }
        }
      });
      if (abstractMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) && toDifferentPackage) {
        if (!isInterfaceTarget) {
          String message = "Can't make " + RefactoringUIUtil.getDescription(abstractMethod, false) +
                           " abstract as it won't be accessible from the subclass.";
          message = CommonRefactoringUtil.capitalize(message);
          conflicts.putValue(abstractMethod, message);
        }
      }
    }
    return conflicts;
  }

  private static void checkInterfaceTarget(MemberInfoBase<? extends GrMember>[] infos, MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfoBase<? extends GrMember> info : infos) {
      GrMember member = info.getMember();

      if (member instanceof PsiField || member instanceof PsiClass) {
        if (!member.hasModifierProperty(PsiModifier.STATIC) && !(member instanceof PsiClass && ((PsiClass)member).isInterface())) {
          String message = RefactoringBundle.message("0.is.not.static.it.cannot.be.moved.to.the.interface", RefactoringUIUtil.getDescription(member, false));
          message = CommonRefactoringUtil.capitalize(message);
          conflictsList.putValue(member, message);
        }
      }

      if (member instanceof PsiField && ((PsiField)member).getInitializer() == null) {
        String message = RefactoringBundle.message("0.is.not.initialized.in.declaration.such.fields.are.not.allowed.in.interfaces",
                                                   RefactoringUIUtil.getDescription(member, false));
        conflictsList.putValue(member, CommonRefactoringUtil.capitalize(message));
      }
    }
  }

  private static void checkSuperclassMembers(PsiClass superClass,
                                             MemberInfoBase<? extends GrMember>[] infos,
                                             MultiMap<PsiElement, String> conflictsList) {
    for (MemberInfoBase<? extends GrMember> info : infos) {
      GrMember member = info.getMember();
      boolean isConflict = false;
      if (member instanceof PsiField) {
        String name = member.getName();

        isConflict = superClass.findFieldByName(name, false) != null;
      }
      else if (member instanceof PsiMethod) {
        PsiSubstitutor superSubstitutor = TypeConversionUtil
          .getSuperClassSubstitutor(superClass, member.getContainingClass(), PsiSubstitutor.EMPTY);
        MethodSignature signature = ((PsiMethod) member).getSignature(superSubstitutor);
        final PsiMethod superClassMethod = MethodSignatureUtil.findMethodBySignature(superClass, signature, false);
        isConflict = superClassMethod != null;
      }

      if (isConflict) {
        String message = RefactoringBundle.message("0.already.contains.a.1",
                                                   RefactoringUIUtil.getDescription(superClass, false),
                                                   RefactoringUIUtil.getDescription(member, false));
        message = CommonRefactoringUtil.capitalize(message);
        conflictsList.putValue(superClass, message);
      }

      if (member instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)member;
        final PsiModifierList modifierList = method.getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
          for (PsiClass subClass : ClassInheritorsSearch.search(superClass)) {
            if (method.getContainingClass() != subClass) {
              MethodSignature signature = ((PsiMethod) member).getSignature(TypeConversionUtil.getSuperClassSubstitutor(superClass, subClass, PsiSubstitutor.EMPTY));
              final PsiMethod wouldBeOverriden = MethodSignatureUtil.findMethodBySignature(subClass, signature, false);
              if (wouldBeOverriden != null && VisibilityUtil.compare(VisibilityUtil.getVisibilityModifier(wouldBeOverriden.getModifierList()),
                                                                     VisibilityUtil.getVisibilityModifier(modifierList)) > 0) {
                conflictsList.putValue(wouldBeOverriden, CommonRefactoringUtil.capitalize(RefactoringUIUtil.getDescription(method, true) + " in super class would clash with local method from " + RefactoringUIUtil.getDescription(subClass, true)));
              }
            }
          }
        }
      }
    }

  }

  private static boolean willBeMoved(PsiElement element, Set<GrMember> movedMembers) {
    PsiElement parent = element;
    while (parent != null) {
      if (movedMembers.contains(parent)) return true;
      parent = parent.getParent();
    }
    return false;
  }

  private static class ConflictingUsagesOfSuperClassMembers extends GrClassMemberReferenceVisitor {

    private final PsiMember myMember;
    private final PsiClass mySubClass;
    private final PsiPackage myTargetPackage;
    private final Set<GrMember> myMovedMembers;
    private final MultiMap<PsiElement, String> myConflicts;

    public ConflictingUsagesOfSuperClassMembers(PsiMember member, PsiClass aClass,
                                                PsiPackage targetPackage,
                                                Set<GrMember> movedMembers,
                                                MultiMap<PsiElement, String> conflicts) {
      super(aClass);
      myMember = member;
      mySubClass = aClass;
      myTargetPackage = targetPackage;
      myMovedMembers = movedMembers;
      myConflicts = conflicts;
    }

    @Override
    protected void visitClassMemberReferenceElement(GrMember classMember, GrReferenceElement ref) {
      if (classMember != null && !willBeMoved(classMember, myMovedMembers)) {
        final PsiClass containingClass = classMember.getContainingClass();
        if (containingClass != null &&
            !PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage) &&
            (classMember.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) ||
             classMember.hasModifierProperty(PsiModifier.PROTECTED) && !mySubClass.isInheritor(containingClass, true))) {
          myConflicts.putValue(myMember, RefactoringUIUtil.getDescription(classMember, true) + " won't be accessible");
        }
      }

    }

  }

  private static class ConflictingUsagesOfSubClassMembers extends GrClassMemberReferenceVisitor {
    private final PsiElement myScope;
    private final Set<GrMember> myMovedMembers;
    private final Set<GrMethod> myAbstractMethods;
    private final PsiClass mySubclass;
    private final PsiClass mySuperClass;
    private final PsiPackage myTargetPackage;
    private final MultiMap<PsiElement, String> myConflictsList;
    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

    ConflictingUsagesOfSubClassMembers(PsiElement scope,
                                       Set<GrMember> movedMembers, Set<GrMethod> abstractMethods,
                                       PsiClass subclass, PsiClass superClass,
                                       PsiPackage targetPackage, MultiMap<PsiElement, String> conflictsList,
                                       InterfaceContainmentVerifier interfaceContainmentVerifier) {
      super(subclass);
      myScope = scope;
      myMovedMembers = movedMembers;
      myAbstractMethods = abstractMethods;
      mySubclass = subclass;
      mySuperClass = superClass;
      myTargetPackage = targetPackage;
      myConflictsList = conflictsList;
      myInterfaceContainmentVerifier = interfaceContainmentVerifier;
    }

    @Override
    protected void visitClassMemberReferenceElement(GrMember classMember, GrReferenceElement ref) {
      if (classMember != null && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
        if (classMember.hasModifierProperty(PsiModifier.STATIC) && !willBeMoved(classMember, myMovedMembers)) {
          final boolean isAccessible = mySuperClass != null ? PsiUtil.isAccessible(classMember, mySuperClass, null) :
                                       myTargetPackage != null ? PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage):
                                       classMember.hasModifierProperty(PsiModifier.PUBLIC);
          if (!isAccessible) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.accessible.from.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = CommonRefactoringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);

          }
          return;
        }
        if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember, myMovedMembers)) {
          if (!existsInSuperClass(classMember)) {
            String message = RefactoringBundle.message("0.uses.1.which.is.not.moved.to.the.superclass",
                                                       RefactoringUIUtil.getDescription(myScope, false),
                                                       RefactoringUIUtil.getDescription(classMember, true));
            message = CommonRefactoringUtil.capitalize(message);
            myConflictsList.putValue(classMember, message);
          }
        }
      }

    }

    private boolean existsInSuperClass(PsiElement classMember) {
      if (!(classMember instanceof PsiMethod)) return false;
      final PsiMethod method = ((PsiMethod)classMember);
      if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) return true;
      if (mySuperClass == null) return false;
      final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
      return methodBySignature != null;
    }
  }


}
