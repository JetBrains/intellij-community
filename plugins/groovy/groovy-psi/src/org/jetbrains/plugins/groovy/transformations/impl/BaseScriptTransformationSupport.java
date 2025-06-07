// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtilKt.findDeclaredDetachedValue;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_BASE_SCRIPT;

public final class BaseScriptTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    if (!(context.getCodeClass() instanceof GroovyScriptClass scriptClass)) return;

    LightMethodBuilder mainMethod = new LightMethodBuilder(scriptClass.getManager(), GroovyLanguage.INSTANCE, "main")
      .setMethodReturnType(PsiTypes.voidType())
      .addParameter("args", new PsiArrayType(PsiType.getJavaLangString(scriptClass.getManager(), scriptClass.getResolveScope())))
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC);

    LightMethodBuilder runMethod = new LightMethodBuilder(scriptClass.getManager(), GroovyLanguage.INSTANCE, "run")
      .setMethodReturnType(TypesUtil.getJavaLangObject(scriptClass))
      .addModifier(PsiModifier.PUBLIC);

    context.addMethod(runMethod, true);
    context.addMethod(mainMethod, true);

    context.setSuperType(getBaseClassType(scriptClass));
  }

  private static @NotNull PsiClassType getBaseClassType(@NotNull GroovyScriptClass scriptClass) {
    PsiClassType type = CachedValuesManager.getCachedValue(scriptClass, () -> CachedValueProvider.Result.create(
      getSuperClassTypeFromBaseScriptAnnotation(scriptClass), scriptClass.getContainingFile()
    ));
    if (type != null) {
      PsiClass resolved = type.resolve();
      if (resolved instanceof GrTypeDefinition && reachableInHierarchy(new HashSet<>(List.of(scriptClass)), (GrTypeDefinition)resolved)) {
        return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_SCRIPT, scriptClass);
      }
      return type;
    }

    final PsiClassType superClassFromDSL = GroovyDslFileIndex.processScriptSuperClasses(scriptClass.getContainingFile());
    if (superClassFromDSL != null) return superClassFromDSL;

    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_SCRIPT, scriptClass);
  }


  private static boolean reachableInHierarchy(Set<PsiClass> forbidden, GrTypeDefinition root) {
    for (PsiClass aSuper : root.getSupers(false)) {
      if (forbidden.contains(aSuper)) {
        return true;
      }
      forbidden.add(aSuper);
      if (aSuper instanceof GrTypeDefinition && reachableInHierarchy(forbidden, (GrTypeDefinition)aSuper)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable PsiClassType getSuperClassTypeFromBaseScriptAnnotation(@NotNull GroovyScriptClass scriptClass) {
    //Groovy BaseScriptASTTransformation works exactly with this priorities
    PsiClassType fromVariable = getSuperClassTypeFromBaseScriptAnnotatedVariable(scriptClass);
    if (fromVariable != null) {
      return fromVariable;
    }

    PsiClassType fromImport = getSuperClassTypeFromBaseScriptAnnotatedImportDefinition(scriptClass);
    if (fromImport != null) {
      return fromImport;
    }

    return getSuperClassTypeFromBaseScriptAnnotatedPackageDefinition(scriptClass);
  }

  private static @Nullable PsiClassType getSuperClassTypeFromBaseScriptAnnotatedImportDefinition(@NotNull GroovyScriptClass scriptClass) {
    GrImportStatement[] importStatements = scriptClass.getContainingFile().getImportStatements();
    for (GrImportStatement importStatement : importStatements) {
      GrModifierList annotations = importStatement.getAnnotationList();
      PsiAnnotation baseScriptAnnotation = annotations.findAnnotation(GROOVY_TRANSFORM_BASE_SCRIPT);
      if (baseScriptAnnotation == null) {
        continue;
      }
      PsiClassType superClassType = getSuperClassTypeFromAnnotationValue(scriptClass, baseScriptAnnotation);
      if (superClassType != null) {
        return superClassType;
      }
    }
    return null;
  }

  private static @Nullable PsiClassType getSuperClassTypeFromBaseScriptAnnotatedPackageDefinition(@NotNull GroovyScriptClass scriptClass) {
    GrPackageDefinition packageDefinition = scriptClass.getContainingFile().getPackageDefinition();
    if (packageDefinition == null) {
      return null;
    }
    PsiModifierList modifierList = packageDefinition.getModifierList();
    if (modifierList == null) {
      return null;
    }
    PsiAnnotation baseScriptAnnotation = modifierList.findAnnotation(GROOVY_TRANSFORM_BASE_SCRIPT);
    if (baseScriptAnnotation == null) {
      return null;
    }
    return getSuperClassTypeFromAnnotationValue(scriptClass, baseScriptAnnotation);
  }

  private static @Nullable PsiClassType getSuperClassTypeFromAnnotationValue(GroovyScriptClass scriptClass, PsiAnnotation baseScriptAnnotation) {
    PsiClass clazz = GrAnnotationUtil.getPsiClass(findDeclaredDetachedValue(baseScriptAnnotation, "value"));
    if (clazz != null) {
      String className = clazz.getQualifiedName();
      if (className == null) clazz.getName();
      if (className != null) {
        return TypesUtil.createTypeByFQClassName(className, scriptClass);
      }
    }
    return null;
  }

  private static @Nullable PsiClassType getSuperClassTypeFromBaseScriptAnnotatedVariable(GroovyScriptClass scriptClass) {
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

  private static @Nullable GrVariableDeclaration findDeclaration(GroovyFile file) {
    if (!PsiSearchHelper.getInstance(file.getProject()).hasIdentifierInFile(file, "BaseScript")) {
      return null;
    }
    for (GrVariableDeclaration declaration : file.getScriptDeclarations(false)) {
      if (declaration.getModifierList().hasAnnotation(GROOVY_TRANSFORM_BASE_SCRIPT)) {
        return declaration;
      }
    }
    return null;
  }
}
