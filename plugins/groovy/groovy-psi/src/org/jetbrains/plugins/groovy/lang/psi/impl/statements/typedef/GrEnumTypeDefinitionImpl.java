// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_ENUM;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrEnumTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrEnumTypeDefinition {

  private static final @NotNull Key<Boolean> PREDEFINED_ENUM_METHOD = Key.create("PREDEFINED_ENUM_METHOD");

  public GrEnumTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyStubElementTypes.ENUM_TYPE_DEFINITION);
  }

  @Override
  public String toString() {
    return "Enumeration definition";
  }

  @Override
  public GrEnumDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyEmptyStubElementTypes.ENUM_BODY);
  }

  @Override
  public boolean isEnum() {
    return true;
  }

  @Override
  public PsiClassType @NotNull [] getExtendsListTypes(boolean includeSynthetic) {
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

  @ApiStatus.Internal
  public List<PsiMethod> getDefEnumMethods(@NotNull TransformationContext context) {
    PsiManagerEx manager = getManager();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiClassType thisType = context.eraseClassType(factory.createType(this, PsiSubstitutor.EMPTY));
    List<PsiMethod> result = Arrays.asList(
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "values")
        .setMethodReturnType(new PsiArrayType(thisType))
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC)
        .addModifier(PsiModifier.STATIC),
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "next")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC),
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "previous")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addModifier(PsiModifier.PUBLIC),
      new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "valueOf")
        .setMethodReturnType(thisType)
        .setContainingClass(this)
        .addParameter("name", CommonClassNames.JAVA_LANG_STRING)
        .addModifier(PsiModifier.PUBLIC)
        .addModifier(PsiModifier.STATIC)
    );
    for (PsiMethod method : result) {
      method.putUserData(PREDEFINED_ENUM_METHOD, true);
    }
    return result;
  }

  public boolean isPredefinedEnumMethod(@NotNull PsiMethod method) {
    return method.getUserData(PREDEFINED_ENUM_METHOD) != null;
  }

  @Override
  public GrEnumConstant @NotNull [] getEnumConstants() {
    GrEnumDefinitionBody body = getBody();
    return body == null ? GrEnumConstant.EMPTY_ARRAY : body.getEnumConstants();
  }

  @Override
  public GrEnumConstantList getEnumConstantList() {
    GrEnumDefinitionBody enumDefinitionBody = getBody();
    return enumDefinitionBody == null ? null : enumDefinitionBody.getEnumConstantList();
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitEnumDefinition(this);
  }

  @Override
  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof GrEnumConstant)) return super.add(psiElement);
    final GrTypeDefinitionBody body = getBody();
    assert body != null;
    GrEnumConstantList list = getEnumConstantList();
    if (list != null) {
      GrEnumConstant[] constants = list.getEnumConstants();
      if (constants.length > 0) {
        list.getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", null);
        list.getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", null);
        return list.add(psiElement);
      }
    }

    PsiElement brace = body.getLBrace();
    return body.addAfter(psiElement, brace);
  }
}
