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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by Max Medvedev on 20/05/14
 */
public class GrTraitType extends PsiClassType {

  private static final Logger LOG = Logger.getInstance(GrTraitType.class);

  private final GrExpression myOriginal;

  private final PsiClassType myExprType;
  private final List<PsiClassType> myTraitTypes;

  private final GlobalSearchScope myResolveScope;

  private final VolatileNotNullLazyValue<PsiType[]> myParameters = new VolatileNotNullLazyValue<PsiType[]>() {
    @NotNull
    @Override
    protected PsiType[] compute() {
      List<PsiType> result = ContainerUtil.newArrayList();
      ContainerUtil.addAll(result, myExprType.getParameters());
      for (PsiClassType type : myTraitTypes) {
        ContainerUtil.addAll(result, type.getParameters());
      }
      return result.toArray(new PsiType[result.size()]);
    }
  };


  public GrTraitType(@NotNull GrExpression original,
                     @NotNull PsiClassType exprType,
                     @NotNull List<PsiClassType> traitTypes,
                     @NotNull GlobalSearchScope resolveScope, LanguageLevel languageLevel) {
    super(languageLevel);
    myOriginal = original;
    myResolveScope = resolveScope;
    myExprType = exprType;
    myTraitTypes = ContainerUtil.newArrayList(traitTypes);
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return myExprType.getPresentableText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, new Function<PsiClassType, String>() {
      @Override
      public String fun(PsiClassType type) {
        return type.getPresentableText();
      }
    }), ", ");
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myExprType.getCanonicalText();
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return myExprType.getCanonicalText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, new Function<PsiClassType, String>() {
      @Override
      public String fun(PsiClassType type) {
        return type.getCanonicalText();
      }
    }), ", ");
  }

  @Override
  public boolean isValid() {
    return myExprType.isValid() && ContainerUtil.find(myTraitTypes, new Condition<PsiClassType>() {
      @Override
      public boolean value(PsiClassType type) {
        return !type.isValid();
      }
    }) == null;
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return false;
  }

  @NotNull
  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrTraitType(myOriginal, myExprType, myTraitTypes, myResolveScope, languageLevel);
  }

  @Nullable
  @Override
  public PsiClass resolve() {
    return getMockTypeDefinition();
  }

  @Override
  public String getClassName() {
    return null;
  }

  @NotNull
  @Override
  public PsiType[] getParameters() {
    return myParameters.getValue();
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    PsiType[] result = new PsiType[myTraitTypes.size() + 1];
    result[0] = myExprType;
    ArrayUtil.copy(myTraitTypes, result, 1);
    return result;
  }

  @NotNull
  @Override
  public ClassResolveResult resolveGenerics() {
    return CachedValuesManager.getCachedValue(myOriginal, new CachedValueProvider<ClassResolveResult>() {
      @Nullable
      @Override
      public Result<ClassResolveResult> compute() {
        final GrTypeDefinition definition = new MockTypeBuilder().buildMockTypeDefinition();
        final PsiSubstitutor substitutor = new SubstitutorBuilder(definition).buildSubstitutor();

        return Result.<ClassResolveResult>create(new TraitResolveResult(definition, substitutor), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @NotNull
  @Override
  public PsiClassType rawType() {
    return new GrTraitType(myOriginal, myExprType.rawType(), ContainerUtil.map(myTraitTypes, new Function<PsiClassType, PsiClassType>() {
      @Override
      public PsiClassType fun(PsiClassType type) {
        return type.rawType();
      }
    }), myResolveScope, myLanguageLevel);
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }

  @Nullable
  public GrTypeDefinition getMockTypeDefinition() {
    return CachedValuesManager.getCachedValue(myOriginal, new CachedValueProvider<GrTypeDefinition>() {
      @Nullable
      @Override
      public Result<GrTypeDefinition> compute() {
        return Result.create(new MockTypeBuilder().buildMockTypeDefinition(), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public PsiClassType getExprType() {
    return myExprType;
  }

  public List<PsiClassType> getTraitTypes() {
    return Collections.unmodifiableList(myTraitTypes);
  }

  public GrTraitType erasure() {
    PsiClassType exprType = (PsiClassType)TypeConversionUtil.erasure(myExprType);
    List<PsiClassType> traitTypes = ContainerUtil.map(myTraitTypes, new Function<PsiClassType, PsiClassType>() {
      @Override
      public PsiClassType fun(PsiClassType type) {
        return (PsiClassType)TypeConversionUtil.erasure(type);
      }
    });
    return new GrTraitType(myOriginal, exprType, traitTypes, myResolveScope, LanguageLevel.JDK_1_5);
  }

  @Nullable
  public static GrTraitType createTraitClassType(@NotNull GrSafeCastExpression safeCastExpression) {
    GrExpression operand = safeCastExpression.getOperand();
    PsiType exprType = operand.getType();
    if (!(exprType instanceof PsiClassType)) return null;

    GrTypeElement typeElement = safeCastExpression.getCastTypeElement();
    if (typeElement == null) return null;
    PsiType type = typeElement.getType();
    if (!GrTraitUtil.isTrait(PsiTypesUtil.getPsiClass(type))) return null;

    return new GrTraitType(safeCastExpression, ((PsiClassType)exprType), Collections.singletonList((PsiClassType)type), safeCastExpression.getResolveScope(),
                           LanguageLevel.JDK_1_5);
  }


  @NotNull
  public static GrTraitType createTraitClassType(@NotNull GrExpression context,
                                                 @NotNull PsiClassType exprType,
                                                 @NotNull List<PsiClassType> traitTypes,
                                                 @NotNull GlobalSearchScope resolveScope) {
    return new GrTraitType(context, exprType, traitTypes, resolveScope, LanguageLevel.JDK_1_5);
  }

  private static class TraitResolveResult implements ClassResolveResult {

    private final GrTypeDefinition myDefinition;
    private final PsiSubstitutor mySubstitutor;

    public TraitResolveResult(GrTypeDefinition definition, PsiSubstitutor substitutor) {
      myDefinition = definition;
      mySubstitutor = substitutor;
    }

    @Override
    public GrTypeDefinition getElement() {
      return myDefinition;
    }

    @NotNull
    @Override
    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    @Override
    public boolean isPackagePrefixPackageReference() {
      return false;
    }

    @Override
    public boolean isAccessible() {
      return true;
    }

    @Override
    public boolean isStaticsScopeCorrect() {
      return true;
    }

    @Override
    public PsiElement getCurrentFileResolveScope() {
      return null;
    }

    @Override
    public boolean isValidResult() {
      return true;
    }
  }

  private class MockTypeBuilder {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myOriginal.getProject());

    @Nullable
    public GrTypeDefinition buildMockTypeDefinition() {
      try {

        StringBuilder buffer = new StringBuilder("class _____________Temp___________ ");
        prepareGenerics(buffer);
        buffer.append(" extends Super implements Trait {}");
        GrTypeDefinition definition = factory.createTypeDefinition(buffer.toString());
        replaceReferenceWith(definition.getExtendsClause(), myExprType);
        addReferencesWith(definition.getImplementsClause(), myTraitTypes, myExprType.getParameterCount());
        return definition;
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }

    private void prepareGenerics(StringBuilder buffer) {
      int count = myExprType.getParameterCount();
      for (PsiClassType trait : myTraitTypes) {
        count += trait.getParameterCount();
      }
      if (count == 0) return;

      buffer.append('<');
      for (int i = 0; i < count; i++) {
        buffer.append("T").append(i).append(",");
      }
      buffer.replace(buffer.length() - 1, buffer.length(), ">");
    }

    private void addReferencesWith(@Nullable GrImplementsClause clause, @NotNull List<PsiClassType> traitTypes, int parameterOffset) {
      LOG.assertTrue(clause != null);
      clause.getReferenceElementsGroovy()[0].delete();
      for (PsiClassType type : traitTypes) {
        processType(clause, type, parameterOffset);
        parameterOffset += type.getParameterCount();
      }
    }

    private void replaceReferenceWith(@Nullable GrReferenceList clause, @NotNull PsiClassType type) {
      LOG.assertTrue(clause != null);
      clause.getReferenceElementsGroovy()[0].delete();
      processType(clause, type, 0);
    }

    private void processType(@NotNull GrReferenceList clause, @NotNull PsiClassType type, int parameterOffset) {
      PsiClass resolved = type.resolve();
      if (resolved != null) {
        String qname = resolved.getQualifiedName();
        StringBuilder buffer = new StringBuilder();
        buffer.append(qname);
        int parameterCount = type.getParameterCount();
        if (parameterCount > 0) {
          buffer.append('<');
          for (int i = 0; i < parameterCount; i++) {
            buffer.append("T").append(parameterOffset + i).append(',');
          }
          buffer.replace(buffer.length() - 1, buffer.length(), ">");
        }

        GrCodeReferenceElement ref = factory.createCodeReferenceElementFromText(buffer.toString());
        clause.add(ref);
      }
    }
  }

  private class SubstitutorBuilder {

    private final GrTypeParameter[] myParameters;
    private int myOffset = 0;

    public SubstitutorBuilder(@NotNull GrTypeDefinition definition) {
      GrTypeParameterList typeParameterList = definition.getTypeParameterList();
      myParameters = typeParameterList != null ? typeParameterList.getTypeParameters() : GrTypeParameter.EMPTY_ARRAY;
    }

    @NotNull
    public PsiSubstitutor buildSubstitutor() {
      if (myParameters.length == 0) return PsiSubstitutor.EMPTY;

      Map<PsiTypeParameter, PsiType> map = ContainerUtil.newLinkedHashMap();
      putMappingAndReturnOffset(map, myExprType);
      for (PsiClassType type : myTraitTypes) {
        putMappingAndReturnOffset(map, type);
      }

      return JavaPsiFacade.getElementFactory(myOriginal.getProject()).createSubstitutor(map);
    }

    private void putMappingAndReturnOffset(@NotNull Map<PsiTypeParameter, PsiType> map, @NotNull PsiClassType type) {
      PsiType[] args = type.getParameters();
      for (int i = 0; i < args.length; i++) {
        map.put(myParameters[myOffset + i], args[i]);
      }
      myOffset += args.length;
    }
  }
}
