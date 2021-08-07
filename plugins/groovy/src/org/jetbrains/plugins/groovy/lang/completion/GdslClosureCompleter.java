// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.ClsMethodImpl;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.ClosureDescriptor;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GdslClosureCompleter extends ClosureCompleter {
  @Override
  protected List<ClosureParameterInfo> getParameterInfos(InsertionContext context,
                                                         PsiMethod method,
                                                         PsiSubstitutor substitutor,
                                                         PsiElement place) {
    final ArrayList<ClosureDescriptor> descriptors = new ArrayList<>();
    GrReferenceExpression ref = (GrReferenceExpression)place;
    PsiType qtype = PsiImplUtil.getQualifierType(ref);
    if (qtype == null) return null;

    GrExpression qualifier = ref.getQualifier();
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) return null;
      processExecutors((PsiClassType)qtype, ref, descriptors);
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
      for (PsiElement parent = ref.getParent(); parent != null; parent = parent.getParent()) {
        if (parent instanceof GrClosableBlock) {
          processExecutors(TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, ref), ref, descriptors);
        }
        else if (parent instanceof GrTypeDefinition) {
          processExecutors(factory.createType(((GrTypeDefinition)parent), PsiType.EMPTY_ARRAY), ref, descriptors);
        }
      }
    }

    for (ClosureDescriptor descriptor : descriptors) {
      if (isMethodApplicable(descriptor, method, ref)) {
        return ContainerUtil.map(descriptor.getParameters(), d -> new ClosureParameterInfo(d.getName(), d.getType()));
      }
    }
    return null;
  }

  private static void processExecutors(
    @NotNull PsiClassType qtype,
    @NotNull GrReferenceExpression ref,
    @NotNull List<ClosureDescriptor> descriptors
  ) {
    GroovyDslFileIndex.processExecutors(qtype, ref, (holder, descriptor) -> {
      holder.consumeClosureDescriptors(descriptor, descriptors::add);
      return true;
    });
  }

  private static boolean isMethodApplicable(
    @NotNull ClosureDescriptor descriptor,
    @NotNull PsiMethod method,
    @NotNull GroovyPsiElement place
  ) {
    String name = descriptor.getMethodName();
    if (!name.equals(method.getName())) {
      return false;
    }

    PsiElement typeContext;
    if (descriptor.getUsePlaceContextForTypes()) {
      typeContext = place;
    }
    else {
      PsiTypeParameterList typeParameterList = method.getTypeParameterList();
      typeContext = typeParameterList != null ? typeParameterList : method;
    }

    List<PsiType> types = new ArrayList<>();
    for (String type : descriptor.getMethodParameterTypes()) {
      types.add(convertToPsiType(type, typeContext));
    }

    final boolean isConstructor = descriptor.isMethodConstructor();
    final MethodSignature signature = MethodSignatureUtil.createMethodSignature(
      name, types.toArray(PsiType.createArray(types.size())), method.getTypeParameters(), PsiSubstitutor.EMPTY, isConstructor
    );
    final GrSignature closureSignature = GrClosureSignatureUtil.createSignature(signature);

    if (method instanceof ClsMethodImpl) method = ((ClsMethodImpl)method).getSourceMirrorMethod();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiType[] typeArray = ContainerUtil.map(
      parameters,
      parameter -> parameter.getType(), PsiType.createArray(parameters.length)
    );
    return GrClosureSignatureUtil.isSignatureApplicable(Collections.singletonList(closureSignature), typeArray, place);
  }

  private static PsiType convertToPsiType(@NlsSafe String type, @NotNull PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }
}
