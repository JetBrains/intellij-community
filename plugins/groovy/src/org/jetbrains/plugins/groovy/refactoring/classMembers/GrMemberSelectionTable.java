/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.classMembers;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.ui.AbstractMemberSelectionTable;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

import javax.swing.*;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrMemberSelectionTable extends AbstractMemberSelectionTable<GrMember, GrMemberInfo> {

  public GrMemberSelectionTable(final List<GrMemberInfo> memberInfos, String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public GrMemberSelectionTable(final List<GrMemberInfo> memberInfos, MemberInfoModel<GrMember, GrMemberInfo> memberInfoModel, String abstractColumnHeader) {
    super(memberInfos, memberInfoModel, abstractColumnHeader);
  }

  @Nullable
  @Override
  protected Object getAbstractColumnValue(GrMemberInfo memberInfo) {
    if (!(memberInfo.getMember() instanceof PsiMethod)) return null;
    if (memberInfo.isStatic()) return null;

    PsiMethod method = (PsiMethod)memberInfo.getMember();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final Boolean fixedAbstract = myMemberInfoModel.isFixedAbstract(memberInfo);
      if (fixedAbstract != null) return fixedAbstract;
    }

    if (!myMemberInfoModel.isAbstractEnabled(memberInfo)) {
      return myMemberInfoModel.isAbstractWhenDisabled(memberInfo);
    }
    else {
      return memberInfo.isToAbstract();
    }
  }

  @Override
  protected boolean isAbstractColumnEditable(int rowIndex) {
    GrMemberInfo info = myMemberInfos.get(rowIndex);
    if (!(info.getMember() instanceof PsiMethod)) return false;
    if (info.isStatic()) return false;

    PsiMethod method = (PsiMethod)info.getMember();
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (myMemberInfoModel.isFixedAbstract(info) != null) {
        return false;
      }
    }

    return info.isChecked() && myMemberInfoModel.isAbstractEnabled(info);
  }


  @Override
  protected void setVisibilityIcon(GrMemberInfo memberInfo, RowIcon icon) {
    PsiMember member = memberInfo.getMember();
    PsiModifierList modifiers = member != null ? member.getModifierList() : null;
    if (modifiers != null) {
      VisibilityIcons.setVisibilityIcon(modifiers, icon);
    }
    else {
      icon.setIcon(IconUtil.getEmptyIcon(true), VISIBILITY_ICON_POSITION);
    }
  }

  @Override
  protected Icon getOverrideIcon(GrMemberInfo memberInfo) {
    PsiMember member = memberInfo.getMember();
    Icon overrideIcon = EMPTY_OVERRIDE_ICON;
    if (member instanceof PsiMethod) {
      if (Boolean.TRUE.equals(memberInfo.getOverrides())) {
        overrideIcon = AllIcons.General.OverridingMethod;
      }
      else if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
        overrideIcon = AllIcons.General.ImplementingMethod;
      }
      else {
        overrideIcon = EMPTY_OVERRIDE_ICON;
      }
    }
    return overrideIcon;
  }
}

