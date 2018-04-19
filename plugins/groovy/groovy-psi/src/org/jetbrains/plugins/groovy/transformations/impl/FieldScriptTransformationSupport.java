// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import static org.jetbrains.plugins.groovy.util.GrFileIndexUtil.hasNameInFile;

public class FieldScriptTransformationSupport implements AstTransformationSupport {
  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (!(context.getCodeClass() instanceof GroovyScriptClass)) return;
    final GroovyScriptClass scriptClass = (GroovyScriptClass)context.getCodeClass();
    if (!hasNameInFile(scriptClass.getContainingFile(), "Field")) {
      return;
    }
    for (GrVariableDeclaration declaration : scriptClass.getContainingFile().getScriptDeclarations(true)) {
      if (declaration.getModifierList().hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD)) {
        for (GrVariable variable : declaration.getVariables()) {
          context.addField(new GrScriptField(variable, scriptClass));
        }
      }
    }
  }
}
