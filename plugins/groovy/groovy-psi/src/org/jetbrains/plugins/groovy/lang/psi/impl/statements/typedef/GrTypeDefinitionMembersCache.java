/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import java.util.*;

/**
 * Created by Max Medvedev on 03/03/14
 */
public class GrTypeDefinitionMembersCache {
  private static final Logger LOG = Logger.getInstance(GrTypeDefinitionMembersCache.class);

  private static final Condition<PsiMethod> CONSTRUCTOR_CONDITION = new Condition<PsiMethod>() {
    @Override
    public boolean value(PsiMethod method) {
      return method.isConstructor();
    }
  };

  private final SimpleModificationTracker myTreeChangeTracker = new SimpleModificationTracker();

  private final GrTypeDefinition myDefinition;

  public GrTypeDefinitionMembersCache(GrTypeDefinition definition) {
    myDefinition = definition;
  }


  public GrMethod[] getCodeMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrMethod[]>() {
      @Nullable
      @Override
      public Result<GrMethod[]> compute() {
        GrTypeDefinitionBody body = myDefinition.getBody();
        GrMethod[] methods = body != null ? body.getMethods() : GrMethod.EMPTY_ARRAY;
        return Result.create(methods, myTreeChangeTracker);
      }
    });
  }

  public GrMethod[] getCodeConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrMethod[]>() {
      @Nullable
      @Override
      public Result<GrMethod[]> compute() {
        GrTypeDefinitionBody body = myDefinition.getBody();
        GrMethod[] methods;
        if (body != null) {
          List<GrMethod> result = ContainerUtil.findAll(body.getMethods(), CONSTRUCTOR_CONDITION);
          methods = result.toArray(new GrMethod[result.size()]);
        }
        else {
          methods = GrMethod.EMPTY_ARRAY;
        }
        return Result.create(methods, myTreeChangeTracker);
      }
    });
  }

  public PsiMethod[] getConstructors() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiMethod[]>() {
      @Nullable
      @Override
      public Result<PsiMethod[]> compute() {
        List<PsiMethod> result = ContainerUtil.findAll(myDefinition.getMethods(), CONSTRUCTOR_CONDITION);
        return Result.create(result.toArray(new PsiMethod[result.size()]), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }


  public PsiClass[] getInnerClasses() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiClass[]>() {
      @Nullable
      @Override
      public Result<PsiClass[]> compute() {
        final GrTypeDefinitionBody body = myDefinition.getBody();
        PsiClass[] inners = body != null ? body.getInnerClasses() : PsiClass.EMPTY_ARRAY;
        return Result.create(inners, myTreeChangeTracker);
      }
    });
  }

  public GrField[] getFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrField[]>() {
      @Nullable
      @Override
      public Result<GrField[]> compute() {
        List<GrField> fields = getFieldsImpl();
        return Result.create(fields.toArray(new GrField[fields.size()]), myTreeChangeTracker, PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  private List<GrField> getFieldsImpl() {
    List<GrField> fields = ContainerUtil.newArrayList(myDefinition.getCodeFields());
    fields.addAll(getSyntheticFields());
    return fields;
  }

  private List<GrField> getSyntheticFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<List<GrField>>() {
      @Nullable
      @Override
      public Result<List<GrField>> compute() {
        return Result.create(AstTransformContributor.runContributorsForFields(myDefinition), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public PsiMethod[] getMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiMethod[]>() {
      @Override
      public Result<PsiMethod[]> compute() {
        List<PsiMethod> result = ContainerUtil.newArrayList();
        GrClassImplUtil.collectMethodsFromBody(myDefinition, result);

        for (PsiMethod method : AstTransformContributor.runContributorsForMethods(myDefinition)) {
          GrClassImplUtil.addExpandingReflectedMethods(result, method);
        }

        for (GrField field : getSyntheticFields()) {
          if (!field.isProperty()) continue;
          ContainerUtil.addIfNotNull(result, field.getSetter());
          Collections.addAll(result, field.getGetters());
        }
        return Result.create(result.toArray(new PsiMethod[result.size()]), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public void dropCaches() {
    myTreeChangeTracker.incModificationCount();
  }

  public static class TraitCollector extends AstTransformContributor {
    private abstract static class TraitProcessor<T extends PsiElement> {
      private final ArrayList<CandidateInfo> result = ContainerUtil.newArrayList();
      private final Set<PsiClass> processed = ContainerUtil.newHashSet();

      public TraitProcessor(@NotNull GrTypeDefinition superClass, @NotNull PsiSubstitutor substitutor) {
        process(superClass, substitutor);
      }

      @NotNull
      public List<CandidateInfo> getResult() {
        return result;
      }

      private void process(@NotNull GrTypeDefinition trait, @NotNull PsiSubstitutor substitutor) {
        assert trait.isTrait();
        if (!processed.add(trait)) return;

        processTrait(trait, substitutor);

        List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(trait.getSuperTypes());
        for (PsiClassType.ClassResolveResult resolveResult :traits) {
          PsiClass superClass = resolveResult.getElement();
          if (GrTraitUtil.isTrait(superClass)) {
            final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, trait, substitutor);
            process((GrTypeDefinition)superClass, superSubstitutor);
          }
        }
      }

      protected abstract void processTrait(@NotNull GrTypeDefinition trait, @NotNull PsiSubstitutor substitutor);

      protected void addCandidate(T element, PsiSubstitutor substitutor) {
        result.add(new CandidateInfo(element, substitutor));
      }
    }

    @Override
    public void collectMethods(@NotNull GrTypeDefinition clazz, Collection<PsiMethod> collector) {
      if (clazz.isInterface() && !clazz.isTrait()) return;

      GrImplementsClause clause = clazz.getImplementsClause();
      if (clause == null) return;
      PsiClassType[] types = clause.getReferencedTypes();

      List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(types);
      if (traits.isEmpty()) return;

      PsiMethod[] codeMethods = clazz.getCodeMethods();
      Set<MethodSignature> existingSignatures = ContainerUtil.newHashSet(ContainerUtil.map(codeMethods, new Function<PsiMethod, MethodSignature>() {
        @Override
        public MethodSignature fun(PsiMethod method) {
          return method.getSignature(PsiSubstitutor.EMPTY);
        }
      }));

      for (PsiClassType.ClassResolveResult resolveResult : traits) {
        GrTypeDefinition trait = (GrTypeDefinition)resolveResult.getElement();
        LOG.assertTrue(trait != null);

        List<CandidateInfo> concreteTraitMethods = new TraitProcessor<PsiMethod>(trait, resolveResult.getSubstitutor()) {
          protected void processTrait(@NotNull GrTypeDefinition trait, @NotNull PsiSubstitutor substitutor) {
            for (GrMethod method : trait.getCodeMethods()) {
              if (!method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
                addCandidate(method, substitutor);
              }
            }

            for (GrField field : trait.getCodeFields()) {
              if (!field.isProperty()) continue;
              for (GrAccessorMethod method : field.getGetters()) {
                addCandidate(method, substitutor);
              }
              GrAccessorMethod setter = field.getSetter();
              if (setter != null) {
                addCandidate(setter, substitutor);
              }
            }
          }
        }.getResult();
        for (CandidateInfo candidateInfo : concreteTraitMethods) {
          List<GrMethod> methodsToAdd = getExpandingMethods(clazz, candidateInfo);
          for (GrMethod impl : methodsToAdd) {
            if (existingSignatures.add(impl.getSignature(PsiSubstitutor.EMPTY))) {
              collector.add(impl);
            }
          }
        }
      }
    }

    @Override
    public void collectFields(@NotNull GrTypeDefinition clazz, Collection<GrField> collector) {
      if (clazz.isInterface() && !clazz.isTrait()) return;

      if (clazz.isTrait()) {
        for (GrField field : clazz.getCodeFields()) {
          collector.add(new GrTraitField(field, clazz, PsiSubstitutor.EMPTY));
        }
      }

      GrImplementsClause clause = clazz.getImplementsClause();
      if (clause == null) return;

      PsiClassType[] types = clause.getReferencedTypes();

      List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(types);
      for (PsiClassType.ClassResolveResult resolveResult : traits) {
        GrTypeDefinition trait = (GrTypeDefinition)resolveResult.getElement();
        LOG.assertTrue(trait != null);

        List<CandidateInfo> traitFields = new TraitProcessor<PsiField>(trait, resolveResult.getSubstitutor()) {
          protected void processTrait(@NotNull GrTypeDefinition trait, @NotNull PsiSubstitutor substitutor) {
            for (GrField field : trait.getCodeFields()) {
              addCandidate(field, substitutor);
            }
          }
        }.getResult();
        for (CandidateInfo candidateInfo : traitFields) {
          collector.add(new GrTraitField(((PsiField)candidateInfo.getElement()), clazz, candidateInfo.getSubstitutor()));
        }
      }

      if (clazz.isTrait()) {
        for (GrField field : clazz.getCodeFields()) {
          collector.add(new GrTraitField(field, clazz, PsiSubstitutor.EMPTY));
        }
      }
    }

    @NotNull
    private static List<GrMethod> getExpandingMethods(@NotNull GrTypeDefinition clazz, @NotNull CandidateInfo candidateInfo) {
      PsiMethod method = (PsiMethod)candidateInfo.getElement();
      GrLightMethodBuilder implementation = GrTraitMethod.create(method, candidateInfo.getSubstitutor()).setContainingClass(clazz);
      implementation.getModifierList().removeModifier(GrModifierFlags.ABSTRACT_MASK);

      GrReflectedMethod[] reflectedMethods = implementation.getReflectedMethods();
      return reflectedMethods.length > 0 ? Arrays.<GrMethod>asList(reflectedMethods) : Collections.<GrMethod>singletonList(implementation);
    }

    @NotNull
    private static List<PsiClassType.ClassResolveResult> getSuperTraitsByCorrectOrder(@NotNull PsiClassType[] types) {
      List<PsiClassType.ClassResolveResult> traits = ContainerUtil.newArrayList();
      for (int i = types.length - 1; i >= 0; i--) {
        PsiClassType.ClassResolveResult resolveResult = types[i].resolveGenerics();
        PsiClass superClass = resolveResult.getElement();

        if (GrTraitUtil.isTrait(superClass)) {
          traits.add(resolveResult);
        }
      }
      return traits;
    }
  }
}
