// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.search.PsiSearchHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import static org.jetbrains.plugins.groovy.util.GrFileIndexUtil.isGroovySourceFile;

public class FieldScriptTransformationSupport implements AstTransformationSupport {
  @NlsSafe private static final String FIELD = "Field";

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (!(context.getCodeClass() instanceof GroovyScriptClass)) return;
    final GroovyScriptClass scriptClass = (GroovyScriptClass)context.getCodeClass();
    final GroovyFile containingFile = scriptClass.getContainingFile();
    Project project = containingFile.getProject();
    if (isGroovySourceFile(containingFile) && !PsiSearchHelper.getInstance(project).hasIdentifierInFile(containingFile, FIELD)) {
      return;
    }
    for (GrVariableDeclaration declaration : containingFile.getScriptDeclarations(true)) {
      if (declaration.getModifierList().hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD)) {
        for (GrVariable variable : declaration.getVariables()) {
          context.addField(new GrScriptField(variable, scriptClass));
        }
      }
    }
  }
}
