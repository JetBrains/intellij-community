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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * Created by Max Medvedev on 25/03/14
 */
public class BaseScriptAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT.equals(annotation.getQualifiedName())) {
      PsiFile file = annotation.getContainingFile();
      if (file instanceof GroovyFile && !(((GroovyFile)file).isScript())) {
        holder.createErrorAnnotation(annotation, GroovyBundle.message("base.script.annotation.is.allowed.only.inside.scripts"));
        return true;
      }

      PsiElement parent = annotation.getParent().getParent();
      if (parent instanceof GrPackageDefinition || parent instanceof GrImportStatement) {
        PsiClass clazz = GrAnnotationUtil.inferClassAttribute(annotation, "value");
        if (!InheritanceUtil.isInheritor(clazz, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = null;
          if (clazz != null) {
            typeText = clazz.getQualifiedName();
            if (typeText == null) clazz.getName();
          }
          if (typeText == null) typeText = CommonClassNames.JAVA_LANG_OBJECT;
          holder.createErrorAnnotation(annotation, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText));
        }
      } else if (parent instanceof GrVariableDeclaration) {
        GrTypeElement typeElement = ((GrVariableDeclaration)parent).getTypeElementGroovy();
        PsiType type = typeElement != null ? typeElement.getType() : null;

        if (!InheritanceUtil.isInheritor(type, GroovyCommonClassNames.GROOVY_LANG_SCRIPT)) {
          String typeText = type != null ? type.getCanonicalText() : CommonClassNames.JAVA_LANG_OBJECT;
          holder.createErrorAnnotation(annotation, GroovyBundle.message("declared.type.0.have.to.extend.script", typeText));
          return true;
        }
      }
    }

    return false;
  }
}
