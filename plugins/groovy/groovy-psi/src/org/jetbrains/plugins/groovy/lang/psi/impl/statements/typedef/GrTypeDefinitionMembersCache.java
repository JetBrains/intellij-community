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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.DelegateSubstitutor;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.*;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.TraitFieldDescriptor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AstTransformContributor;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.IMPLEMENTED_FQN;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.INDEX_ID;

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
        final List<PsiClass> result = ContainerUtil.newArrayList();
        final GrTypeDefinitionBody body = myDefinition.getBody();
        if (body != null) ContainerUtil.addAll(result, body.getInnerClasses());
        result.addAll(AstTransformContributor.runContributors(myDefinition).getClasses());
        return Result.create(result.toArray(new PsiClass[result.size()]), myTreeChangeTracker);
      }
    });
  }

  public GrField[] getFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<GrField[]>() {
      @Nullable
      @Override
      public Result<GrField[]> compute() {
        List<GrField> fields = getFieldsImpl();
        return Result.create(fields.toArray(new GrField[fields.size()]), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  private List<GrField> getFieldsImpl() {
    List<GrField> fields = ContainerUtil.newArrayList(myDefinition.getCodeFields());
    fields.addAll(new TraitCollector().collectFields());
    fields.addAll(getSyntheticFields());
    return fields;
  }

  private Collection<GrField> getSyntheticFields() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<Collection<GrField>>() {
      @Nullable
      @Override
      public Result<Collection<GrField>> compute() {
        return Result.create(AstTransformContributor.runContributors(myDefinition).getFields(), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public PsiMethod[] getMethods() {
    return CachedValuesManager.getCachedValue(myDefinition, new CachedValueProvider<PsiMethod[]>() {
      @Override
      public Result<PsiMethod[]> compute() {
        Collection<PsiMethod> result = ContainerUtil.newLinkedHashSet();

        GrClassImplUtil.collectMethodsFromBody(myDefinition, result);
        result.addAll(new TraitCollector().collectMethods(result));
        for (PsiMethod method : AstTransformContributor.runContributors(myDefinition).getMethods()) {
          GrClassImplUtil.addExpandingReflectedMethods(result, method);
        }

        for (GrField field : getSyntheticFields()) {
          if (!field.isProperty()) continue;
          ContainerUtil.addIfNotNull(result, field.getSetter());
          Collections.addAll(result, field.getGetters());
        }

        result = GrClassImplUtil.filterOutAccessors(result);

        return Result.create(result.toArray(new PsiMethod[result.size()]), myTreeChangeTracker,
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  public void dropCaches() {
    myTreeChangeTracker.incModificationCount();
  }

  private class TraitCollector {
    private abstract class TraitProcessor<T extends PsiElement> {
      private final ArrayList<CandidateInfo> result = ContainerUtil.newArrayList();
      private final Set<PsiClass> processed = ContainerUtil.newHashSet();

      public TraitProcessor(@NotNull PsiClass superClass, @NotNull PsiSubstitutor substitutor) {
        process(superClass, substitutor);
      }

      @NotNull
      public List<CandidateInfo> getResult() {
        return result;
      }

      private void process(@NotNull PsiClass trait, @NotNull PsiSubstitutor substitutor) {
        if (!processed.add(trait)) return;

        processTrait(trait, substitutor);

        List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(trait.getSuperTypes());
        for (PsiClassType.ClassResolveResult resolveResult : traits) {
          PsiClass superClass = resolveResult.getElement();
          if (GrTraitUtil.isTrait(superClass)) {
            final PsiSubstitutor superSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, trait, substitutor);
            process(superClass, superSubstitutor);
          }
        }
      }

      protected abstract void processTrait(@NotNull PsiClass trait, @NotNull PsiSubstitutor substitutor);

      protected void addCandidate(T element, PsiSubstitutor substitutor) {
        result.add(new CandidateInfo(element, substitutor));
      }
    }

    @NotNull
    public List<PsiMethod> collectMethods(@NotNull Collection<PsiMethod> codeMethods) {
      if (myDefinition.isInterface() && !myDefinition.isTrait()) return Collections.emptyList();

      GrImplementsClause clause = myDefinition.getImplementsClause();
      if (clause == null) return Collections.emptyList();
      PsiClassType[] types = clause.getReferencedTypes();

      List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(types);
      if (traits.isEmpty()) return Collections.emptyList();

      Set<MethodSignature> existingSignatures =
        ContainerUtil.newHashSet(ContainerUtil.map(codeMethods, new Function<PsiMethod, MethodSignature>() {
          @Override
          public MethodSignature fun(PsiMethod method) {
            return method.getSignature(PsiSubstitutor.EMPTY);
          }
        }));

      List<PsiMethod> result = ContainerUtil.newArrayList();

      for (PsiClassType.ClassResolveResult resolveResult : traits) {
        PsiClass trait = resolveResult.getElement();
        LOG.assertTrue(trait != null);

        List<CandidateInfo> concreteTraitMethods = new TraitProcessor<PsiMethod>(trait, resolveResult.getSubstitutor()) {
          protected void processTrait(@NotNull PsiClass trait, @NotNull PsiSubstitutor substitutor) {
            if (trait instanceof GrTypeDefinition) {
              for (GrMethod method : ((GrTypeDefinition)trait).getCodeMethods()) {
                if (!method.getModifierList().hasExplicitModifier(PsiModifier.ABSTRACT)) {
                  addCandidate(method, substitutor);
                }
              }

              for (GrField field : ((GrTypeDefinition)trait).getCodeFields()) {
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
            else if (trait instanceof ClsClassImpl) {
              for (PsiMethod method : trait.getMethods()) {
                if (AnnotationUtil.isAnnotated(method, IMPLEMENTED_FQN, false)) {
                  addCandidate(method, substitutor);
                }
              }
              final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(trait.getProject());
              final String helperFQN = trait.getQualifiedName();
              final PsiClass traitHelper = psiFacade.findClass(helperFQN + "$Trait$Helper", trait.getResolveScope());
              if (traitHelper == null) return;
              final PsiType classType = TypesUtil.createJavaLangClassType(
                psiFacade.getElementFactory().createType(trait), trait.getProject(), trait.getResolveScope()
              );
              final PsiSubstitutor delegateSubstitutor = getSubstitutor(substitutor, trait);
              for (PsiMethod method : traitHelper.getMethods()) {
                if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
                final PsiParameter[] parameters = method.getParameterList().getParameters();
                if (parameters.length <= 0) continue;
                final PsiParameter self = parameters[0];
                if (self.getType().equals(classType)) {
                  addCandidate(GrGdkMethodImpl.createGdkMethod(method, true, "via @Trait"), delegateSubstitutor);
                }
              }
            }
          }

          @NotNull
          private DelegateSubstitutor getSubstitutor(@NotNull final PsiSubstitutor substitutor,
                                                     @NotNull final PsiClass trait) {
            final Map<String, PsiType> substitutionMap = ContainerUtil.newTroveMap();
            for (PsiTypeParameter parameter : trait.getTypeParameters()) {
              substitutionMap.put(parameter.getName(), substitutor.substitute(parameter));
            }
            return new DelegateSubstitutor(substitutor) {
              @Override
              public PsiType substitute(@Nullable PsiType type) {
                final PsiType substituted = super.substitute(type);
                if (type != null && (substituted == null || substituted.equals(type))) {
                  final PsiType byName = substitutionMap.get(type.getCanonicalText());
                  return byName == null ? substituted : byName;
                }
                else {
                  return substituted;
                }
              }
            };
          }
        }.getResult();
        for (CandidateInfo candidateInfo : concreteTraitMethods) {
          List<GrMethod> methodsToAdd = getExpandingMethods(candidateInfo);
          for (GrMethod impl : methodsToAdd) {
            if (existingSignatures.add(impl.getSignature(PsiSubstitutor.EMPTY))) {
              result.add(impl);
            }
          }
        }
      }
      return result;
    }

    @NotNull
    public List<GrField> collectFields() {
      if (myDefinition.isInterface() && !myDefinition.isTrait()) return Collections.emptyList();

      List<GrField> result = ContainerUtil.newArrayList();

      if (myDefinition.isTrait()) {
        for (GrField field : myDefinition.getCodeFields()) {
          result.add(new GrTraitField(field, myDefinition, PsiSubstitutor.EMPTY));
        }
      }

      GrImplementsClause clause = myDefinition.getImplementsClause();
      if (clause == null) return result;
      PsiClassType[] types = clause.getReferencedTypes();

      List<PsiClassType.ClassResolveResult> traits = getSuperTraitsByCorrectOrder(types);
      for (PsiClassType.ClassResolveResult resolveResult : traits) {
        PsiClass trait = resolveResult.getElement();
        LOG.assertTrue(trait != null);

        List<CandidateInfo> traitFields = new TraitProcessor<PsiField>(trait, resolveResult.getSubstitutor()) {
          protected void processTrait(@NotNull final PsiClass trait, @NotNull final PsiSubstitutor substitutor) {
            if (trait instanceof GrTypeDefinition) {
              for (GrField field : ((GrTypeDefinition)trait).getCodeFields()) {
                addCandidate(field, substitutor);
              }
            }
            else if (trait instanceof ClsClassImpl) {
              final PsiClass traitFieldHelper = JavaPsiFacade.getInstance(trait.getProject()).findClass(
                trait.getQualifiedName() + "$Trait$FieldHelper", trait.getResolveScope()
              );
              if (traitFieldHelper == null) return;

              final VirtualFile virtualFile = traitFieldHelper.getContainingFile().getVirtualFile();
              FileBasedIndex.getInstance().processValues(
                INDEX_ID,
                FileBasedIndex.getFileId(virtualFile),
                virtualFile,
                new FileBasedIndex.ValueProcessor<Collection<TraitFieldDescriptor>>() {
                  @Override
                  public boolean process(VirtualFile file, Collection<TraitFieldDescriptor> values) {
                    for (TraitFieldDescriptor descriptor : values) {
                      final GrLightField field = new GrLightField(trait, descriptor.name, descriptor.typeString);
                      if (descriptor.isStatic) {
                        field.getModifierList().addModifier(STATIC_MASK);
                      }
                      field.getModifierList().addModifier(descriptor.isPublic ? PUBLIC_MASK : PRIVATE_MASK);
                      addCandidate(field, substitutor);
                    }
                    return true;
                  }
                },
                trait.getResolveScope()
              );
            }
          }
        }.getResult();
        for (CandidateInfo candidateInfo : traitFields) {
          result.add(new GrTraitField(((PsiField)candidateInfo.getElement()), myDefinition, candidateInfo.getSubstitutor()));
        }
      }

      return result;
    }

    @NotNull
    private List<GrMethod> getExpandingMethods(@NotNull CandidateInfo candidateInfo) {
      PsiMethod method = (PsiMethod)candidateInfo.getElement();
      GrLightMethodBuilder implementation = GrTraitMethod.create(method, candidateInfo.getSubstitutor()).setContainingClass(myDefinition);
      implementation.getModifierList().removeModifier(ABSTRACT_MASK);

      GrReflectedMethod[] reflectedMethods = implementation.getReflectedMethods();
      return reflectedMethods.length > 0 ? Arrays.<GrMethod>asList(reflectedMethods) : Collections.<GrMethod>singletonList(implementation);
    }

    @NotNull
    private List<PsiClassType.ClassResolveResult> getSuperTraitsByCorrectOrder(@NotNull PsiClassType[] types) {
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
