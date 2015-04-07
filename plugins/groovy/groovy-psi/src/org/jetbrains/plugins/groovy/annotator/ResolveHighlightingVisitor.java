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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessChecker;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.List;

/**
* Created by Max Medvedev on 21/03/14
*/
class ResolveHighlightingVisitor extends GroovyRecursiveElementVisitor {
  private final GrUnresolvedAccessChecker myReferenceChecker;
  private final List<HighlightInfo> myInfos;

  public ResolveHighlightingVisitor(@NotNull GroovyFileBase file, @NotNull Project project, @NotNull List<HighlightInfo> collector) {
    myReferenceChecker = new GrUnresolvedAccessChecker(file, project);
    myInfos = collector;
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    final int size = myInfos.size();
    super.visitReferenceExpression(referenceExpression);
    if (size == myInfos.size()) {
      List<HighlightInfo> infos = myReferenceChecker.checkReferenceExpression(referenceExpression);
      if (infos != null) {
        ContainerUtil.addAllNotNull(myInfos, infos);
      }
    }
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    final int size = myInfos.size();
    super.visitCodeReferenceElement(refElement);
    if (size == myInfos.size()) {
      HighlightInfo info = myReferenceChecker.checkCodeReferenceElement(refElement);
      if (info != null) {
        myInfos.add(info);
      }
    }
  }
}
