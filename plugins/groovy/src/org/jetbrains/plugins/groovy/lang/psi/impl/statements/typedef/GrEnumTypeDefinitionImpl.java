/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameterListBuilder;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import static org.jetbrains.plugins.groovy.GroovyFileType.GROOVY_LANGUAGE;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrEnumTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrEnumTypeDefinition {
  @NonNls
  private static final String JAVA_LANG_ENUM = "java.lang.Enum";
  private static final String ENUM_SIMPLE_NAME = "Enum";

  public GrEnumTypeDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrEnumTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ENUM_DEFINITION);
  }

  public String toString() {
    return "Enumeration definition";
  }

  public GrEnumDefinitionBody getBody() {
    return (GrEnumDefinitionBody)findChildByType(GroovyElementTypes.ENUM_BODY);
  }

  public boolean isEnum() {
    return true;
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return new PsiClassType[]{createEnumType()};
  }

  protected String[] getExtendsNames() {
    return new String[]{ENUM_SIMPLE_NAME};
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

  @NotNull
  public GrField[] getFields() {
    GrField[] bodyFields = super.getFields();
    GrEnumConstant[] enumConstants = getEnumConstants();
    if (bodyFields.length == 0) return enumConstants;
    if (enumConstants.length == 0) return bodyFields;
    return ArrayUtil.mergeArrays(bodyFields, enumConstants, GrField.class);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @NotNull PsiElement place) {
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint == null || classHint.shouldProcess(ClassHint.ResolveKind.METHOD)) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      final String name = nameHint == null ? null : nameHint.getName(state);
      for (PsiMethod method : generateDefEnumMethods()) {
        if (name == null || name.equals(method.getName())) {
          if (!processor.execute(method, state)) return false;
        }
      }
    }

    return super.processDeclarations(processor, state, lastParent, place);
  }

  private volatile PsiMethod[] defMethods = null;

  private synchronized PsiMethod[] generateDefEnumMethods() {
    if (defMethods == null) {
      defMethods = new PsiMethod[3];
      final PsiManagerEx manager = getManager();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
      defMethods[0] = new LightMethodBuilder(manager, GROOVY_LANGUAGE,
                                             "values",
                                             new LightParameterListBuilder(manager, GROOVY_LANGUAGE),
                                             new LightModifierList(manager, GROOVY_LANGUAGE, "public", "static")
      ).setReturnType(factory.createTypeFromText(CommonClassNames.JAVA_UTIL_COLLECTION + "<" + getName() + ">", this)).setContainingClass(this);

      defMethods[1] = new LightMethodBuilder(manager, GROOVY_LANGUAGE,
                                             "next",
                                             new LightParameterListBuilder(manager, GROOVY_LANGUAGE),
                                             new LightModifierList(manager, GROOVY_LANGUAGE, "public")
      ).setReturnType(factory.createType(this)).setContainingClass(this);
      defMethods[2] = new LightMethodBuilder(manager, GROOVY_LANGUAGE,
                                             "previous",
                                             new LightParameterListBuilder(manager, GROOVY_LANGUAGE),
                                             new LightModifierList(manager, GROOVY_LANGUAGE, "public")
      ).setReturnType(factory.createType(this)).setContainingClass(this);
    }
    return defMethods;
  }

  public GrEnumConstant[] getEnumConstants() {
    GrEnumConstantList list = getEnumConstantList();
    if (list != null) return list.getEnumConstants();
    return GrEnumConstant.EMPTY_ARRAY;
  }

  public GrEnumConstantList getEnumConstantList() {
    GrEnumDefinitionBody enumDefinitionBody = getBody();
    if (enumDefinitionBody != null) return enumDefinitionBody.getEnumConstantList();
    return null;
  }
}
