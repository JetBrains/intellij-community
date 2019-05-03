// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.classMembers.MemberInfoBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public class GrMemberInfo extends MemberInfoBase<GrMember> {
  public GrMemberInfo(GrMember member) {
    this(member, false, null);
  }

  public GrMemberInfo(GrMember member, boolean isSuperClass, GrReferenceList sourceReferenceList) {
    super(member);
    LOG.assertTrue(member.isValid());
    mySourceReferenceList = sourceReferenceList;
    if (member instanceof GrMethod) {
      GrMethod method = (GrMethod)member;
      displayName = PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME |
                                                                             PsiFormatUtilBase.SHOW_TYPE |
                                                                             PsiFormatUtilBase.TYPE_AFTER |
                                                                             PsiFormatUtilBase.SHOW_PARAMETERS,
                                               PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER);
      PsiMethod[] superMethods = method.findSuperMethods();
      if (superMethods.length > 0) {
        overrides = !superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT);
      }
      else {
        overrides = null;
      }

      isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    }
    else if (member instanceof GrField) {
      GrField field = (GrField)member;
      displayName = PsiFormatUtil
        .formatVariable(field, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER, PsiSubstitutor.EMPTY);
      isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      overrides = null;
    }
    else if (member instanceof GrTypeDefinition) {
      GrTypeDefinition aClass = (GrTypeDefinition)member;

      if (isSuperClass) {
        if (aClass.isInterface()) {
          displayName = RefactoringBundle.message("member.info.implements.0", aClass.getName());
          overrides = Boolean.FALSE;
        }
        else {
          displayName = RefactoringBundle.message("member.info.extends.0", aClass.getName());
          overrides = Boolean.TRUE;
        }
      }
      else {
        displayName = aClass.getName();
        overrides = null;
      }

      isStatic = aClass.hasModifierProperty(PsiModifier.STATIC);
    }
    else {
      LOG.assertTrue(false);
      isStatic = false;
      displayName = "";
      overrides = null;
    }
  }

  public GrReferenceList getSourceReferenceList() {
    return mySourceReferenceList;
  }

  public static List<GrMemberInfo> extractClassMembers(GrTypeDefinition subclass, Filter<GrMember> filter, boolean extractInterfacesDeep) {
    List<GrMemberInfo> members = new ArrayList<>();
    extractClassMembers(subclass, members, filter, extractInterfacesDeep);
    return members;
  }

  public static void extractClassMembers(PsiClass subclass,
                                         List<GrMemberInfo> result,
                                         Filter<GrMember> filter,
                                         final boolean extractInterfacesDeep) {

    if (!(subclass instanceof GrTypeDefinition)) return;


    if (extractInterfacesDeep) {
      extractSuperInterfaces(subclass, filter, result, new HashSet<>());
    }
    else {
      PsiClass[] interfaces = subclass.getInterfaces();
      GrReferenceList sourceRefList = subclass.isInterface()
                                      ? ((GrTypeDefinition)subclass).getExtendsClause()
                                      : ((GrTypeDefinition)subclass).getImplementsClause();
      for (PsiClass anInterface : interfaces) {
        if (anInterface instanceof GrTypeDefinition && filter.includeMember((GrMember)anInterface)) {
          result.add(new GrMemberInfo((GrMember)anInterface, true, sourceRefList));
        }
      }
    }


    PsiClass[] innerClasses = subclass.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      if (innerClass instanceof GrTypeDefinition && filter.includeMember((GrMember)innerClass)) {
        result.add(new GrMemberInfo((GrMember)innerClass));
      }
    }

    GrMethod[] methods = ((GrTypeDefinition)subclass).getCodeMethods();
    for (GrMethod method : methods) {
      if (!method.isConstructor() && filter.includeMember(method)) {
        result.add(new GrMemberInfo(method));
      }
    }

    GrField[] fields = ((GrTypeDefinition)subclass).getCodeFields();
    for (final GrField field : fields) {
      if (filter.includeMember(field)) {
        result.add(new GrMemberInfo(field));
      }
    }
  }

  private static void extractSuperInterfaces(final PsiClass subclass,
                                             final Filter<GrMember> filter,
                                             final List<GrMemberInfo> result,
                                             Set<PsiClass> processed) {
    if (!processed.contains(subclass)) {
      processed.add(subclass);
      if (subclass instanceof GrTypeDefinition) {
        extractSuperInterfacesFromReferenceList(((GrTypeDefinition)subclass).getExtendsClause(), filter, result, processed);
        extractSuperInterfacesFromReferenceList(((GrTypeDefinition)subclass).getImplementsClause(), filter, result, processed);
      }
    }
  }

  private static void extractSuperInterfacesFromReferenceList(final GrReferenceList referenceList,
                                                              final Filter<GrMember> filter,
                                                              final List<GrMemberInfo> result,
                                                              final Set<PsiClass> processed) {
    if (referenceList != null) {
      final PsiClassType[] extendsListTypes = referenceList.getReferencedTypes();
      for (PsiClassType extendsListType : extendsListTypes) {
        final PsiClass aSuper = extendsListType.resolve();
        if (aSuper instanceof GrTypeDefinition) {
          if (aSuper.isInterface()) {
            if (filter.includeMember((GrMember)aSuper)) {
              result.add(new GrMemberInfo((GrMember)aSuper, true, referenceList));
            }
          }
          else {
            extractSuperInterfaces(aSuper, filter, result, processed);
          }
        }
      }
    }
  }

  private final GrReferenceList mySourceReferenceList;
}
