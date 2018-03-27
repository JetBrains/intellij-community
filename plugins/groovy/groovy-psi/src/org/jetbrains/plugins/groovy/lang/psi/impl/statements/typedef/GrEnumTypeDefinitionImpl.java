/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_ENUM;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrEnumTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrEnumTypeDefinition {

  public GrEnumTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ENUM_DEFINITION);
  }

  public String toString() {
    return "Enumeration definition";
  }

  @Override
  public GrEnumDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyElementTypes.ENUM_BODY);
  }

  @Override
  public boolean isEnum() {
    return true;
  }

  @Override
  @NotNull
  public PsiClassType[] getExtendsListTypes(boolean includeSynthetic) {
    return new PsiClassType[]{createEnumType()};
  }

  private PsiClassType createEnumType() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass enumClass = facade.findClass(JAVA_LANG_ENUM, getResolveScope());
    PsiElementFactory factory = facade.getElementFactory();
    if (enumClass != null) {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], factory.createType(this));
      }

      return factory.createType(enumClass, substitutor);
    }
    return TypesUtil.createTypeByFQClassName(JAVA_LANG_ENUM, this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (ResolveUtil.shouldProcessMethods(processor.getHint(ElementClassHint.KEY))) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      final String name = nameHint == null ? null : nameHint.getName(state);
      for (PsiMethod method : getDefEnumMethods()) {
        if (name == null || name.equals(method.getName())) {
          if (!processor.execute(method, state)) return false;
        }
      }
    }

    return super.processDeclarations(processor, state, lastParent, place);
  }

  private PsiMethod[] getDefEnumMethods() {
    return CachedValuesManager.getCachedValue(this, () -> {
      PsiMethod[] defMethods = new PsiMethod[4];
      final PsiManagerEx manager = getManager();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
      final PsiClassType thisType = factory.createType(this);
      defMethods[0] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "values")
        .setMethodReturnType(new PsiArrayType(thisType))
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC)
        .addModifier(PsiModifier.STATIC);

      defMethods[1] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "next")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC);

      defMethods[2] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "previous")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC);

      defMethods[3] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "valueOf")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addParameter("name", CommonClassNames.JAVA_LANG_STRING)
        .addModifier(PsiModifier.PUBLIC)
        .addModifier(PsiModifier.STATIC);

      return CachedValueProvider.Result.create(defMethods, this);
    });
  }

  @NotNull
  @Override
  public GrEnumConstant[] getEnumConstants() {
    GrEnumDefinitionBody body = getBody();
    return body == null ? GrEnumConstant.EMPTY_ARRAY : body.getEnumConstants();
  }

  @Override
  public GrEnumConstantList getEnumConstantList() {
    GrEnumDefinitionBody enumDefinitionBody = getBody();
    return enumDefinitionBody == null ? null : enumDefinitionBody.getEnumConstantList();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumDefinition(this);
  }
}
