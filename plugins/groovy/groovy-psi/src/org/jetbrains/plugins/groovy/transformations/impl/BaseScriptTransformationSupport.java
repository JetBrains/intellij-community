/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

public class BaseScriptTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (!(context.getCodeClass() instanceof GroovyScriptClass)) return;
    GroovyScriptClass scriptClass = (GroovyScriptClass)context.getCodeClass();

    LightMethodBuilder mainMethod = new LightMethodBuilder(scriptClass.getManager(), GroovyLanguage.INSTANCE, "main")
      .setMethodReturnType(PsiType.VOID)
      .addParameter("args", new PsiArrayType(PsiType.getJavaLangString(scriptClass.getManager(), scriptClass.getResolveScope())))
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);

    LightMethodBuilder runMethod = new LightMethodBuilder(scriptClass.getManager(), GroovyLanguage.INSTANCE, "run")
      .setMethodReturnType(TypesUtil.getJavaLangObject(scriptClass))
      .addModifier(PsiModifier.PUBLIC);

    context.addMethod(runMethod, true);
    context.addMethod(mainMethod, true);

    context.setSuperType(getBaseClassType(scriptClass));
  }

  @NotNull
  private static PsiClassType getBaseClassType(@NotNull GroovyScriptClass scriptClass) {
    PsiClassType type = getSuperClassTypeFromBaseScriptAnnotatedVariable(scriptClass);
    if (type != null) return type;

    final PsiClassType superClassFromDSL = GroovyDslFileIndex.processScriptSuperClasses(scriptClass.getContainingFile());
    if (superClassFromDSL != null) return superClassFromDSL;

    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_SCRIPT, scriptClass);
  }

  @Nullable
  private static PsiClassType getSuperClassTypeFromBaseScriptAnnotatedVariable(GroovyScriptClass scriptClass) {
    return CachedValuesManager.getCachedValue(scriptClass, () -> CachedValueProvider.Result.create(
      doGetSuperClassType(scriptClass), scriptClass.getContainingFile()
    ));
  }

  private static PsiClassType doGetSuperClassType(GroovyScriptClass scriptClass) {
    GrVariableDeclaration declaration = findDeclaration(scriptClass.getContainingFile());
    if (declaration != null) {


      GrModifierList modifierList = declaration.getModifierList();
      if (modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT) != null) {
        GrTypeElement typeElement = declaration.getTypeElementGroovy();
        if (typeElement != null) {
          PsiType type = typeElement.getType();
          if (type instanceof PsiClassType) {
            return (PsiClassType)type;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static GrVariableDeclaration findDeclaration(GroovyFile file) {
    final Ref<GrVariableDeclaration> ref = Ref.create();
    file.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
        super.visitVariableDeclaration(variableDeclaration);
        if (variableDeclaration.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT) != null) {
          ref.set(variableDeclaration);
        }
      }

      @Override
      public void visitElement(@NotNull GroovyPsiElement element) {
        if (ref.isNull()) {
          super.visitElement(element);
        }
      }
    });

    return ref.get();
  }
}
