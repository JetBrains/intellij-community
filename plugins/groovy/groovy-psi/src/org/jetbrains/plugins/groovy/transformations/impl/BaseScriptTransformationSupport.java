// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.impl;

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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import static org.jetbrains.plugins.groovy.util.GrFileIndexUtil.hasNameInFile;

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
      GrTypeElement typeElement = declaration.getTypeElementGroovy();
      if (typeElement != null) {
        PsiType type = typeElement.getType();
        if (type instanceof PsiClassType) {
          return (PsiClassType)type;
        }
      }
    }
    return null;
  }

  @Nullable
  private static GrVariableDeclaration findDeclaration(GroovyFile file) {
    if (!hasNameInFile(file, "BaseScript")) {
      return null;
    }
    for (GrVariableDeclaration declaration : file.getScriptDeclarations(false)) {
      if (declaration.getModifierList().hasAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT)) {
        return declaration;
      }
    }
    return null;
  }
}
