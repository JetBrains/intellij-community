/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

public abstract class BaseInspection extends GroovySuppressableInspectionTool {
  private final String m_shortName = InspectionProfileEntry.getShortName(getClass().getSimpleName());

  public static final String ASSIGNMENT_ISSUES = "Assignment issues";
  public static final String CONFUSING_CODE_CONSTRUCTS = "Potentially confusing code constructs";
  public static final String CONTROL_FLOW = "Control Flow";
  public static final String PROBABLE_BUGS = "Probable bugs";
  public static final String ERROR_HANDLING = "Error handling";
  public static final String GPATH = "GPath inspections";
  public static final String METHOD_METRICS = "Method Metrics";
  public static final String THREADING_ISSUES = "Threading issues";
  public static final String VALIDITY_ISSUES = "Validity issues";
  public static final String ANNOTATIONS_ISSUES = "Annotations verifying";

  @NotNull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Override
  @NotNull
  public String getShortName() {
    return m_shortName;
  }

  @NotNull
  protected BaseInspectionVisitor buildGroovyVisitor(@NotNull ProblemsHolder problemsHolder, boolean onTheFly) {
    final BaseInspectionVisitor visitor = buildVisitor();
    visitor.setProblemsHolder(problemsHolder);
    visitor.setOnTheFly(onTheFly);
    visitor.setInspection(this);
    return visitor;
  }


  @Nullable
  protected String buildErrorString(Object... args) {
    return null;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  @Nullable
  protected GroovyFix buildFix(@NotNull PsiElement location) {
    return null;
  }

  @Nullable
  protected GroovyFix[] buildFixes(@NotNull PsiElement location) {
    return null;
  }

  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile psiFile, @NotNull InspectionManager inspectionManager, boolean isOnTheFly) {
    if (!(psiFile instanceof GroovyFileBase)) {
      return super.checkFile(psiFile, inspectionManager, isOnTheFly);
    }
    final GroovyFileBase groovyFile = (GroovyFileBase) psiFile;

    final ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, psiFile, isOnTheFly);
    final BaseInspectionVisitor visitor = buildGroovyVisitor(problemsHolder, isOnTheFly);
    groovyFile.accept(visitor);
    return problemsHolder.getResultsArray();

  }

  @NotNull
  protected abstract BaseInspectionVisitor buildVisitor();
}
