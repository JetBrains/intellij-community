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
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrAccessibilityChecker;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

public class InaccessibleElementVisitor extends GroovyRecursiveElementVisitor {
  private final GrAccessibilityChecker myReferenceChecker;
  private final VisitorCallback myCallback;
  private int myTriggerCounter = 0;

  public InaccessibleElementVisitor(GroovyFileBase file, Project project, VisitorCallback callback) {
    myCallback = callback;
    myReferenceChecker = new GrAccessibilityChecker(file, project);
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    final int oldValue = myTriggerCounter;
    super.visitReferenceExpression(referenceExpression);
    if (oldValue == myTriggerCounter) {
      HighlightInfo info = myReferenceChecker.checkReferenceExpression(referenceExpression);
      if (info != null) {
        myCallback.trigger(referenceExpression, info);
        myTriggerCounter++;
      }
    }
  }

  @Override
  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
    final int oldValue = myTriggerCounter;
    super.visitCodeReferenceElement(refElement);
    if (oldValue == myTriggerCounter) {
      HighlightInfo info = myReferenceChecker.checkCodeReferenceElement(refElement);
      if (info != null) {
        myCallback.trigger(refElement, info);
        myTriggerCounter++;
      }
    }
  }
}
