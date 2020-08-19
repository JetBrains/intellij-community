// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.NlsSafe;
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
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("base.script.annotation.is.allowed.only.inside.scripts")).range(annotation).create();
        return true;
      }

      PsiElement pparent = annotation.getParent().getParent();
      if (pparent instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration)pparent).getTypeElementGroovy();
        PsiType type = typeElement != null ? typeElement.getType() : null;

        if (!InheritanceUtil.isInheritor(type, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = type != null ? type.getCanonicalText() : CommonClassNames.JAVA_LANG_OBJECT;
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText)).range(annotation).create();
          return true;
        }
      }
      else if (pparent instanceof GrPackageDefinition || pparent instanceof GrImportStatement) {
        PsiClass clazz = GrAnnotationUtil.inferClassAttribute(annotation, "value");
        if (!InheritanceUtil.isInheritor(clazz, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = getTypeText(clazz);
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText)).range(annotation).create();
        }
      }
    }

    return false;
  }

  @NotNull
  @NlsSafe
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
