// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsClassImpl;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.stubs.ShareableStubTreeSerializer;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.VirtualFileGist;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightModifierList;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitFieldsFileIndex.TraitFieldDescriptor;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyTraitMethodsFileIndex;

import java.io.IOException;
import java.util.*;

import static com.intellij.psi.PsiModifier.ABSTRACT;
import static org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags.*;
import static org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.MirrorsKt.withType;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRAIT;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRAIT_IMPLEMENTED;

/**
 * @author Max Medvedev
 */
public final class GrTraitUtil {
  private static final Logger LOG = Logger.getInstance(GrTraitUtil.class);
  private static final PsiTypeMapper ID_MAPPER = new PsiTypeMapper() {
    @Override
    public PsiType visitClassType(@NotNull PsiClassType classType) {
      return classType;
    }
  };

  @Contract("null -> false")
  public static boolean isInterface(@Nullable PsiClass aClass) {
    return aClass != null && aClass.isInterface() && !(aClass instanceof GrTypeDefinition && ((GrTypeDefinition)aClass).isTrait());
  }

  public static boolean isMethodAbstract(@NotNull PsiMethod method) {
    return method.getModifierList().hasExplicitModifier(ABSTRACT) ||
           isInterface(method.getContainingClass()) && !method.hasModifierProperty(PsiModifier.DEFAULT);
  }

  public static List<PsiClass> getSelfTypeClasses(@NotNull PsiClass trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      List<PsiClass> result = new ArrayList<>();
      InheritanceUtil.processSupers(trait, true, clazz -> {
        if (isTrait(clazz)) {
          PsiAnnotation annotation = AnnotationUtil.findAnnotation(clazz, "groovy.transform.SelfType");
          if (annotation != null) {
            result.addAll(
              GrAnnotationUtil.getClassArrayValue(annotation, "value", false)
            );
          }
        }
        return true;
      });
      return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  public static @NotNull String getTraitFieldPrefix(@NotNull PsiClass aClass) {
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
           aClass instanceof ClsClassImpl && aClass.isInterface() && AnnotationUtil.isAnnotated(aClass, GROOVY_TRAIT, 0) ||
           getDefaultMethods(aClass).length != 0;
  }

  public static GrMethod[] getDefaultMethods(@Nullable PsiClass aClass) {
    if (!(aClass instanceof GrTypeDefinition grTypeDefinition) || !aClass.isInterface()) return GrMethod.EMPTY_ARRAY;
    return Arrays.stream(grTypeDefinition.getCodeMethods())
      .filter(m -> m.getModifierList().hasExplicitModifier(PsiModifier.DEFAULT))
      .toArray(GrMethod[]::new);
  }

  public static @NotNull Collection<PsiMethod> getCompiledTraitConcreteMethods(final @NotNull ClsClassImpl trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      final Collection<PsiMethod> result = new ArrayList<>();
      doCollectCompiledTraitMethods(trait, result);
      return CachedValueProvider.Result.create(result, trait);
    });
  }

  private static void doCollectCompiledTraitMethods(final ClsClassImpl trait, final Collection<? super PsiMethod> result) {
    for (PsiMethod method : trait.getMethods()) {
      if (AnnotationUtil.isAnnotated(method, GROOVY_TRAIT_IMPLEMENTED, 0)) {
        result.add(method);
      }
    }

    for (PsiMethod staticMethod : GroovyTraitMethodsFileIndex.getStaticTraitMethods(trait)) {
      result.add(createTraitMethodFromCompiledHelperMethod(staticMethod, trait));
    }
  }

  private static PsiMethod createTraitMethodFromCompiledHelperMethod(PsiMethod compiledMethod, ClsClassImpl trait) {
    final LightMethodBuilder result = new LightMethodBuilder(trait.getManager(), compiledMethod.getName());
    result.setOriginInfo("via @Trait");
    result.addModifier(PsiModifier.STATIC);

    for (PsiTypeParameter parameter : compiledMethod.getTypeParameters()) {
      result.addTypeParameter(parameter);
    }

    final PsiTypeVisitor<PsiType> corrector = createCorrector(compiledMethod, trait);

    final PsiParameter[] methodParameters = compiledMethod.getParameterList().getParameters();
    for (int i = 1; i < methodParameters.length; i++) {
      final PsiParameter originalParameter = methodParameters[i];
      final PsiType correctedType = originalParameter.getType().accept(corrector);
      result.addParameter(withType(originalParameter, correctedType));
    }

    for (PsiClassType type : compiledMethod.getThrowsList().getReferencedTypes()) {
      final PsiType correctedType = type.accept(corrector);
      result.addException(correctedType instanceof PsiClassType ? (PsiClassType)correctedType : type);
    }

    final PsiType originalType = compiledMethod.getReturnType();
    result.setMethodReturnType(originalType == null ? null : originalType.accept(corrector));

    final PsiClass traitSource = trait.getSourceMirrorClass();
    final PsiMethod sourceMethod = traitSource == null ? null : traitSource.findMethodBySignature(result, false);
    result.setNavigationElement(sourceMethod != null ? sourceMethod : compiledMethod);

    return result;
  }

  private static @NotNull PsiTypeMapper createCorrector(final PsiMethod compiledMethod, final PsiClass trait) {
    final PsiTypeParameter[] traitTypeParameters = trait.getTypeParameters();
    if (traitTypeParameters.length == 0) return ID_MAPPER;

    final Map<String, PsiTypeParameter> substitutionMap = new HashMap<>();
    for (PsiTypeParameter parameter : traitTypeParameters) {
      substitutionMap.put(parameter.getName(), parameter);
    }

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(trait.getProject()).getElementFactory();
    return new PsiTypeMapper() {
      @Override
      public PsiType visitClassType(@NotNull PsiClassType originalType) {
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

  public static @NotNull Collection<GrField> getCompiledTraitFields(final @NotNull ClsClassImpl trait) {
    return CachedValuesManager.getCachedValue(trait, () -> {
      final Collection<GrField> result = new ArrayList<>();
      doCollectCompiledTraitFields(trait, result);
      return CachedValueProvider.Result.create(result, trait);
    });
  }

  private static void doCollectCompiledTraitFields(ClsClassImpl trait, Collection<? super GrField> result) {
    VirtualFile traitFile = trait.getContainingFile().getVirtualFile();
    if (traitFile == null) return;
    VirtualFile helperFile = traitFile.getParent().findChild(trait.getName() + GroovyTraitFieldsFileIndex.HELPER_SUFFIX);
    if (helperFile == null) return;

    Collection<TraitFieldDescriptor> values = GROOVY_TRAIT_FIELDS_GIST.getFileData(trait.getProject(), helperFile);
    if (values != null) {
      values.forEach(descriptor -> result.add(createTraitField(descriptor, trait)));
    }
  }

  private static GrLightField createTraitField(TraitFieldDescriptor descriptor, PsiClass trait) {
    GrLightField field = new GrLightField(trait, descriptor.name, descriptor.typeString);
    GrLightModifierList modifierList = field.getModifierList();
    if ((descriptor.flags & TraitFieldDescriptor.STATIC) != 0) modifierList.addModifier(STATIC_MASK);
    modifierList.addModifier((descriptor.flags & TraitFieldDescriptor.PUBLIC) != 0 ? PUBLIC_MASK : PRIVATE_MASK);
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(trait.getProject());
    for (String annotationText : descriptor.annotations) {
      GrAnnotation annotation = factory.createAnnotationFromText(annotationText, modifierList);
      modifierList.addAnnotation(annotation);
    }
    return field;
  }

  private static final VirtualFileGist<Collection<TraitFieldDescriptor>> GROOVY_TRAIT_FIELDS_GIST =
    GistManager.getInstance().newVirtualFileGist("GROOVY_TRAIT_FIELDS", 1, new GroovyTraitFieldsFileIndex(), (project, file) -> {
      try {
        return GroovyTraitFieldsFileIndex.index(file.contentsToByteArray());
      }
      catch (IOException e) {
        return List.of();
      }
    });

  public static final VirtualFileGist<ByteArraySequence> GROOVY_TRAIT_METHODS_GIST =
    GistManager.getInstance().newVirtualFileGist("GROOVY_TRAIT_METHODS", 1, new GroovyTraitMethodsFileIndex().getValueExternalizer(), (project, file) -> {
      try {
        @Nullable PsiJavaFileStub stub = GroovyTraitMethodsFileIndex.index(file, file.contentsToByteArray());
        if (stub == null) return null;
        BufferExposingByteArrayOutputStream buffer = new BufferExposingByteArrayOutputStream();
        new ShareableStubTreeSerializer().serialize(stub, buffer);
        return buffer.toByteArraySequence();
      }
      catch (IOException e) {
        return ByteArraySequence.EMPTY;
      }
    });
}