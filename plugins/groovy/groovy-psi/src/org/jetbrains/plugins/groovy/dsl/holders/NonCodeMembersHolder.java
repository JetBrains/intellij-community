// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.*;
import org.jetbrains.plugins.groovy.dsl.toplevel.ClassContextFilter;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.extensions.impl.NamedArgumentDescriptorImpl;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;
import java.util.function.Consumer;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.shouldProcessMethods;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.shouldProcessProperties;

/**
 * @author peter
 */
public class NonCodeMembersHolder implements CustomMembersHolder {

  private static final Logger LOG = Logger.getInstance(NonCodeMembersHolder.class);

  public static final Key<@Nls String> DOCUMENTATION = Key.create("GdslDocumentation");
  public static final Key<@NlsSafe String> DOCUMENTATION_URL = Key.create("GdslDocumentationUrl");

  private final List<PsiVariable> myVariables = new ArrayList<>();
  private final List<PsiMethod> myMethods = new ArrayList<>();
  private final List<ClosureDescriptor> myClosureDescriptors = new ArrayList<>();

  public static NonCodeMembersHolder generateMembers(@NotNull List<? extends Descriptor> methods, @NotNull PsiFile file) {
    Map<List<? extends Descriptor>, NonCodeMembersHolder> map = CachedValuesManager.getCachedValue(
      file, () -> {
        final Map<List<? extends Descriptor>, NonCodeMembersHolder> map1 = CollectionFactory.createConcurrentSoftMap();
        return CachedValueProvider.Result.create(map1, PsiModificationTracker.MODIFICATION_COUNT);
      });

    NonCodeMembersHolder result = map.get(methods);
    if (result == null) {
      map.put(methods, result = new NonCodeMembersHolder(methods, file));
    }
    return result;
  }

  public NonCodeMembersHolder() {
  }

  private NonCodeMembersHolder(@NotNull List<? extends Descriptor> data, @NotNull PsiFile file) {
    final PsiManager manager = file.getManager();
    for (Descriptor descriptor : data) {
      if (descriptor instanceof ClosureDescriptor) {
        myClosureDescriptors.add((ClosureDescriptor)descriptor);
      }
      else if (descriptor instanceof VariableDescriptor) {
        myVariables.add(createVariable((VariableDescriptor)descriptor, file, manager));
      }
      else {
        //declarationType == DeclarationType.METHOD
        myMethods.add(createMethod((MethodDescriptor)descriptor, file, manager));
      }
    }
  }

  public void addDeclaration(@NotNull PsiElement element) {
    if (element instanceof PsiMethod) {
      myMethods.add((PsiMethod)element);
    }
    else if (element instanceof PsiVariable) {
      myVariables.add((PsiVariable)element);
    }
    else {
      LOG.error("Unknown declaration: " + element);
    }
  }

  private static PsiVariable createVariable(@NotNull VariableDescriptor descriptor, PsiElement place, PsiManager manager) {
    String name = descriptor.getName();
    final String type = descriptor.getType();
    return new GrLightVariable(manager, name, type, Collections.emptyList(), place.getContainingFile());
  }

  private static GrLightMethodBuilder createMethod(@NonNls MethodDescriptor descriptor, PsiElement place, PsiManager manager) {
    final GrLightMethodBuilder method = new GrLightMethodBuilder(manager, descriptor.getName()).addModifier(PsiModifier.PUBLIC);

    if (descriptor.isConstructor()) {
      method.setConstructor(true);
    }
    else {
      method.setReturnType(convertToPsiType(descriptor.getReturnType(), place));
    }

    List<NamedParameterDescriptor> descriptorNamedParams = descriptor.getNamedParameters();
    if (!descriptorNamedParams.isEmpty()) {
      Map<String, NamedArgumentDescriptor> namedParams = new HashMap<>();
      for (NamedParameterDescriptor paramDescriptor : descriptorNamedParams) {
        String typeString = paramDescriptor.getType();
        GdslNamedParameter parameter = new GdslNamedParameter(
          paramDescriptor.getName(), paramDescriptor.getDoc(), place, typeString
        );
        namedParams.put(
          paramDescriptor.getName(),
          new NamedArgumentDescriptorImpl(NamedArgumentDescriptor.Priority.ALWAYS_ON_TOP, parameter) {
            @Override
            public boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
              return ClassContextFilter.isSubtype(type, context.getContainingFile(), typeString);
            }
          }
        );
      }
      method.addParameter("args", convertToPsiType(CommonClassNames.JAVA_UTIL_MAP, place));
      method.setNamedParameters(namedParams);
    }

    for (VariableDescriptor paramDescriptor : descriptor.getParameters()) {
      method.addParameter(paramDescriptor.getName(), convertToPsiType(paramDescriptor.getType(), place));
    }

    if (descriptor.isStatic()) {
      method.addModifier(PsiModifier.STATIC);
    }

    final PsiElement bindsTo = descriptor.getBindsTo();
    if (bindsTo != null) {
      method.setNavigationElement(bindsTo);
    }

    final List<String> toThrow = descriptor.getThrows();
    for (String o : toThrow) {
      final PsiType psiType = convertToPsiType(o, place);
      if (psiType instanceof PsiClassType) {
        method.addException((PsiClassType)psiType);
      }
    }

    String doc = descriptor.getDoc();
    if (doc != null) {
      method.putUserData(DOCUMENTATION, doc);
    }

    String docUrl = descriptor.getDocUrl();
    if (docUrl != null) {
      method.putUserData(DOCUMENTATION_URL, docUrl);
    }

    String qName = descriptor.getContainingClass();
    if (qName != null) {
      PsiClass foundClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(qName, place.getResolveScope());
      if (foundClass != null) {
        method.setContainingClass(foundClass);
      }
    }
    return method;
  }

  private static PsiType convertToPsiType(String type, PsiElement place) {
    return JavaPsiFacade.getElementFactory(place.getProject()).createTypeFromText(type, place);
  }

  @Override
  public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor _processor, ResolveState state) {
    String hint = ResolveUtil.getNameHint(_processor);
    ElementClassHint classHint = _processor.getHint(ElementClassHint.KEY);
    if (shouldProcessMethods(classHint)) {
      for (PsiMethod declaration : myMethods) {
        if (checkName(hint, declaration) && !_processor.execute(declaration, state)) return false;
      }
    }
    if (shouldProcessProperties(classHint)) {
      for (PsiVariable declaration : myVariables) {
        if (checkName(hint, declaration) && !_processor.execute(declaration, state)) return false;
      }
    }
    return true;
  }

  private static boolean checkName(String hint, PsiNamedElement declaration) {
    if (hint != null && !isConstructor(declaration)) {
      return hint.equals(declaration.getName());
    }
    return true;
  }

  private static boolean isConstructor(PsiElement declaration) {
    return declaration instanceof PsiMethod && ((PsiMethod)declaration).isConstructor();
  }

  @Override
  public void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer) {
    myClosureDescriptors.forEach(consumer);
  }
}
