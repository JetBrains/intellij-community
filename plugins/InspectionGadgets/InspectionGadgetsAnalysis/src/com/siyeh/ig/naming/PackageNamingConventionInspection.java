/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefPackage;
import com.intellij.codeInspection.ui.ConventionOptionsPanel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiPackageStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseGlobalInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.BaseSharedLocalInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageNamingConventionInspection extends BaseGlobalInspection {

  private static final int DEFAULT_MIN_LENGTH = 3;
  private static final int DEFAULT_MAX_LENGTH = 16;
  /**
   * @noinspection PublicField
   */
  @NonNls
  public String m_regex = "[a-z]*";

  /**
   * @noinspection PublicField
   */
  public int m_minLength = DEFAULT_MIN_LENGTH;

  /**
   * @noinspection PublicField
   */
  public int m_maxLength = DEFAULT_MAX_LENGTH;

  protected Pattern m_regexPattern = Pattern.compile(m_regex);

  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("package.naming.convention.display.name");
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity, @NotNull AnalysisScope analysisScope,
                                                @NotNull InspectionManager inspectionManager,
                                                @NotNull GlobalInspectionContext globalInspectionContext) {
    if (!(refEntity instanceof RefPackage)) {
      return null;
    }
    @NonNls final String name = StringUtil.getShortName(refEntity.getQualifiedName());
    if (InspectionsBundle.message("inspection.reference.default.package").equals(name)) {
      return null;
    }

    final int length = name.length();
    if (length == 0) {
      return null;
    }
    if (length < m_minLength) {
      final String errorString = InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.short", name);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
    if (length > m_maxLength) {
      final String errorString = InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    if (matcher.matches()) {
      return null;
    }
    else {
      final String errorString =
        InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch", name, m_regex);
      return new CommonProblemDescriptor[]{inspectionManager.createProblemDescriptor(errorString)};
    }
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    m_regexPattern = Pattern.compile(m_regex);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new ConventionOptionsPanel(this, "m_minLength", "m_maxLength", "m_regex", "m_regexPattern");
  }

  boolean isValid(String name) {
    final int length = name.length();
    if (length < m_minLength) {
      return false;
    }
    if (m_maxLength > 0 && length > m_maxLength) {
      return false;
    }
    final Matcher matcher = m_regexPattern.matcher(name);
    return matcher.matches();
  }

  @Override
  @Nullable
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new LocalPackageNamingConventionInspection(this);
  }

  private static class LocalPackageNamingConventionInspection extends BaseSharedLocalInspection<PackageNamingConventionInspection> {

    public LocalPackageNamingConventionInspection(PackageNamingConventionInspection inspection) {
      super(inspection);
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
      final String name = (String)infos[0];
      if (name.length() < mySettingsDelegate.m_minLength) {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.short", name);
      }
      else if (name.length() > mySettingsDelegate.m_maxLength) {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.long", name);
      }
      else {
        return InspectionGadgetsBundle.message("package.naming.convention.problem.descriptor.regex.mismatch",
                                               name, mySettingsDelegate.m_regex);
      }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
      return new BaseInspectionVisitor() {

        @Override
        public void visitPackageStatement(PsiPackageStatement statement) {
          final PsiJavaCodeReferenceElement reference = statement.getPackageReference();
          if (reference == null) {
            return;
          }
          final String text = reference.getText();
          int start = 0;
          int index = text.indexOf('.', start);
          while (index > 0) {
            final String name = text.substring(start, index);
            if (!mySettingsDelegate.isValid(name)) {
              registerErrorAtOffset(reference, start, index - start, name);
            }
            start = index + 1;
            index = text.indexOf('.', start);
          }
          final String lastName = text.substring(start);
          if (!lastName.isEmpty() && !mySettingsDelegate.isValid(lastName)) {
            registerErrorAtOffset(reference, start, lastName.length(), lastName);
          }
        }
      };
    }
  }
}
