/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import com.intellij.psi.PsiClass;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;

/**
 * User: Dmitry.Krasilschikov
 * Date: 18.02.2008
 */
public class DynamicPropertyDialog extends DynamicDialog {
  private final GrReferenceExpression myReferenceExpression;
  private final GrArgumentLabel myLabel;

  public DynamicPropertyDialog(GrReferenceExpression referenceExpression) {
    super(referenceExpression, QuickfixUtil.createSettings(referenceExpression), GroovyExpectedTypesUtil.calculateTypeConstraints(referenceExpression));
    myReferenceExpression = referenceExpression;
    myLabel = null;
    setTitle(GroovyBundle.message("add.dynamic.property", referenceExpression.getReferenceName()));
    setUpTypeLabel(GroovyBundle.message("dynamic.method.property.type"));
  }

  public DynamicPropertyDialog(GrArgumentLabel label, PsiClass targetClass) {
    super(label, QuickfixUtil.createSettings(label, targetClass), new SupertypeConstraint[]{SupertypeConstraint.create(label.getNamedArgument().getExpression().getType())});
    myLabel = label;
    myReferenceExpression = null;
  }
}
