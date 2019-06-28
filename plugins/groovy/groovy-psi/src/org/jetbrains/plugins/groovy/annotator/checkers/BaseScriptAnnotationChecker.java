// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

public class BaseScriptAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT.equals(annotation.getQualifiedName())) {
      PsiFile file = annotation.getContainingFile();
      if (file instanceof GroovyFile && !(((GroovyFile)file).isScript())) {
        holder.createErrorAnnotation(annotation, GroovyBundle.message("base.script.annotation.is.allowed.only.inside.scripts"));
        return true;
      }

      PsiElement pparent = annotation.getParent().getParent();
      if (pparent instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration)pparent).getTypeElementGroovy();
        PsiType type = typeElement != null ? typeElement.getType() : null;

        if (!InheritanceUtil.isInheritor(type, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = type != null ? type.getCanonicalText() : CommonClassNames.JAVA_LANG_OBJECT;
          holder.createErrorAnnotation(annotation, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText));
          return true;
        }
      }
      else if (pparent instanceof GrPackageDefinition || pparent instanceof GrImportStatement) {
        PsiClass clazz = GrAnnotationUtil.inferClassAttribute(annotation, "value");
        if (!InheritanceUtil.isInheritor(clazz, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = getTypeText(clazz);
          holder.createErrorAnnotation(annotation, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText));
        }
      }
    }

    return false;
  }

  @NotNull
  public String getTypeText(@Nullable PsiClass clazz) {
    if (clazz == null) {
      return CommonClassNames.JAVA_LANG_OBJECT;
    }
    String fqn = clazz.getQualifiedName();
    if (fqn != null) {
      return fqn;
    }
    String name = clazz.getName();
    if (name != null) {
      return name;
    }
    return CommonClassNames.JAVA_LANG_OBJECT;
  }
}
