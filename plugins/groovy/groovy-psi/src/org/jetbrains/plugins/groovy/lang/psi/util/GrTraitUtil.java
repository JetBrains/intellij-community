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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.TraitFieldDescriptor;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitMethodsFileIndex;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.psi.PsiModifier.ABSTRACT;
import static org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRAIT;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRAIT_IMPLEMENTED;

/**
 * @author Max Medvedev
 * @since 16.05.2014
 */
public class GrTraitUtil {
  private static final Logger LOG = Logger.getInstance(GrTraitUtil.class);
  private static final PsiTypeMapper ID_MAPPER = new PsiTypeMapper() {
    @Override
    public PsiType visitClassType(PsiClassType classType) {
      return classType;
    }
  };

  @Contract("null -> false")
  public static boolean isInterface(@Nullable PsiClass aClass) {
    return aClass != null && aClass.isInterface() && !isTrait(aClass);
  }

  public static boolean isMethodAbstract(@NotNull PsiMethod method) {
    return method.getModifierList().hasExplicitModifier(ABSTRACT) || isInterface(method.getContainingClass());
  }

  public static List<PsiClass> getSelfTypeClasses(@NotNull PsiClass trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      List<PsiClass> result = ContainerUtil.newArrayList();
      InheritanceUtil.processSupers(trait, true, clazz -> {
        if (isTrait(clazz)) {
          PsiAnnotation annotation = AnnotationUtil.findAnnotation(clazz, "groovy.transform.SelfType");
          if (annotation != null) {
            result.addAll(
              GrAnnotationUtil.getClassArrayValue(annotation, "value")
            );
          }
        }
        return true;
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
    });
  }

  @NotNull
  public static String getTraitFieldPrefix(@NotNull PsiClass aClass) {
    String qname = aClass.getQualifiedName();
    LOG.assertTrue(qname != null, aClass.getClass());

    String[] identifiers = qname.split("\\.");

    StringBuilder buffer = new StringBuilder();
    for (String identifier : identifiers) {
      buffer.append(identifier).append('_');
    }

    buffer.append('_');
    return buffer.toString();
  }

  @Contract("null -> false")
  public static boolean isTrait(@Nullable PsiClass aClass) {
    return aClass instanceof GrTypeDefinition && ((GrTypeDefinition)aClass).isTrait() ||
           aClass instanceof ClsClassImpl && aClass.isInterface() && AnnotationUtil.isAnnotated(aClass, GROOVY_TRAIT, false);
  }

  @NotNull
  public static Collection<PsiMethod> getCompiledTraitConcreteMethods(@NotNull final ClsClassImpl trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      final Collection<PsiMethod> result = ContainerUtil.newArrayList();
      doCollectCompiledTraitMethods(trait, result);
      return CachedValueProvider.Result.create(result, trait);
    });
  }

  private static void doCollectCompiledTraitMethods(final ClsClassImpl trait, final Collection<PsiMethod> result) {
    for (PsiMethod method : trait.getMethods()) {
      if (AnnotationUtil.isAnnotated(method, GROOVY_TRAIT_IMPLEMENTED, false)) {
        result.add(method);
      }
    }

    VirtualFile traitFile = trait.getContainingFile().getVirtualFile();
    if (traitFile == null) return;
    VirtualFile helperFile = traitFile.getParent().findChild(trait.getName() + GroovyTraitMethodsFileIndex.HELPER_SUFFIX);
    if (helperFile == null) return;
    int key = FileBasedIndex.getFileId(helperFile);
    List<PsiJavaFileStub> values = FileBasedIndex.getInstance().getValues(
      GroovyTraitMethodsFileIndex.INDEX_ID, key, trait.getResolveScope()
    );
    values.forEach(root -> ((PsiJavaFileStubImpl)root).setPsi((PsiJavaFile)trait.getContainingFile()));
    values.stream().map(
      root -> root.getChildrenStubs().get(0).getChildrenStubs()
    ).<StubElement>flatMap(
      Collection::stream
    ).filter(
      stub -> stub instanceof PsiMethodStub
    ).forEach(
      stub -> result.add(createTraitMethodFromCompiledHelperMethod(((PsiMethodStub)stub).getPsi(), trait))
    );
  }

  private static PsiMethod createTraitMethodFromCompiledHelperMethod(PsiMethod compiledMethod, ClsClassImpl trait) {
    final GrLightMethodBuilder result = new GrLightMethodBuilder(trait.getManager(), compiledMethod.getName());
    result.setOriginInfo("via @Trait");
    result.addModifier(PsiModifier.STATIC);

    for (PsiTypeParameter parameter : compiledMethod.getTypeParameters()) {
      result.getTypeParameterList().addParameter(parameter);
    }

    final PsiTypeVisitor<PsiType> corrector = createCorrector(compiledMethod, trait);

    final PsiParameter[] methodParameters = compiledMethod.getParameterList().getParameters();
    for (int i = 1; i < methodParameters.length; i++) {
      final PsiParameter originalParameter = methodParameters[i];
      final PsiType correctedType = originalParameter.getType().accept(corrector);
      final String name = originalParameter.getName();
      assert name != null : compiledMethod;
      result.addParameter(name, correctedType, false);
    }

    for (PsiClassType type : compiledMethod.getThrowsList().getReferencedTypes()) {
      final PsiType correctedType = type.accept(corrector);
      result.getThrowsList().addReference(correctedType instanceof PsiClassType ? (PsiClassType)correctedType : type);
    }

    final PsiType originalType = compiledMethod.getReturnType();
    result.setReturnType(originalType == null ? null : originalType.accept(corrector));

    final PsiClass traitSource = trait.getSourceMirrorClass();
    final PsiMethod sourceMethod = traitSource == null ? null : traitSource.findMethodBySignature(result, false);
    result.setNavigationElement(sourceMethod != null ? sourceMethod : compiledMethod);

    return result;
  }

  @NotNull
  private static PsiTypeMapper createCorrector(final PsiMethod compiledMethod, final PsiClass trait) {
    final PsiTypeParameter[] traitTypeParameters = trait.getTypeParameters();
    if (traitTypeParameters.length == 0) return ID_MAPPER;

    final Map<String, PsiTypeParameter> substitutionMap = ContainerUtil.newTroveMap();
    for (PsiTypeParameter parameter : traitTypeParameters) {
      substitutionMap.put(parameter.getName(), parameter);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(trait.getProject()).getElementFactory();
    return new PsiTypeMapper() {
      @Override
      public PsiType visitClassType(PsiClassType originalType) {
        final PsiClass resolved = originalType.resolve();
        // if resolved to method parameter -> return as is
        if (resolved instanceof PsiTypeParameter && compiledMethod.equals(((PsiTypeParameter)resolved).getOwner())) return originalType;
        final PsiType[] typeParameters = originalType.getParameters();
        final PsiTypeParameter byName = substitutionMap.get(originalType.getCanonicalText());
        if (byName != null) {
          assert typeParameters.length == 0;
          return elementFactory.createType(byName);
        }
        if (resolved == null) return originalType;
        if (typeParameters.length == 0) return originalType;    // do not go deeper

        final Ref<Boolean> hasChanges = Ref.create(false);
        final PsiTypeVisitor<PsiType> $this = this;
        final PsiType[] substitutes = ContainerUtil.map2Array(typeParameters, PsiType.class, type -> {
          final PsiType mapped = type.accept($this);
          hasChanges.set(mapped != type);
          return mapped;
        });
        return hasChanges.get() ? elementFactory.createType(resolved, substitutes) : originalType;
      }
    };
  }

  @NotNull
  public static Collection<GrField> getCompiledTraitFields(@NotNull final ClsClassImpl trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      final Collection<GrField> result = ContainerUtil.newArrayList();
      doCollectCompiledTraitFields(trait, result);
      return CachedValueProvider.Result.create(result, trait);
    });
  }

  private static void doCollectCompiledTraitFields(ClsClassImpl trait, Collection<GrField> result) {
    VirtualFile traitFile = trait.getContainingFile().getVirtualFile();
    if (traitFile == null) return;
    VirtualFile helperFile = traitFile.getParent().findChild(trait.getName() + GroovyTraitFieldsFileIndex.HELPER_SUFFIX);
    if (helperFile == null) return;
    int key = FileBasedIndex.getFileId(helperFile);
    final List<Collection<TraitFieldDescriptor>> values = FileBasedIndex.getInstance().getValues(
      GroovyTraitFieldsFileIndex.INDEX_ID, key, trait.getResolveScope()
    );
    values.forEach(descriptors -> descriptors.forEach(descriptor -> result.add(createTraitField(descriptor, trait))));
  }

  private static GrLightField createTraitField(TraitFieldDescriptor descriptor, PsiClass trait) {
    GrLightField field = new GrLightField(trait, descriptor.name, descriptor.typeString);
    if ((descriptor.flags & TraitFieldDescriptor.STATIC) != 0) field.getModifierList().addModifier(STATIC_MASK);
    field.getModifierList().addModifier((descriptor.flags & TraitFieldDescriptor.PUBLIC) != 0 ? PUBLIC_MASK : PRIVATE_MASK);
    return field;
  }
}