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
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.CustomMembersGenerator;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolverProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class NonCodeMembersHolder implements CustomMembersHolder {
  public static final Key<String> DOCUMENTATION = Key.create("GdslDocumentation");
  public static final Key<String> DOCUMENTATION_URL = Key.create("GdslDocumentationUrl");
  private final List<PsiElement> myDeclarations = new ArrayList<>();

  public static NonCodeMembersHolder generateMembers(List<Map> methods, final PsiFile place) {
    Map<List<Map>, NonCodeMembersHolder> map = CachedValuesManager.getCachedValue(
      place, () -> {
        final Map<List<Map>, NonCodeMembersHolder> map1 = ContainerUtil.createConcurrentSoftMap();
        return CachedValueProvider.Result.create(map1, PsiModificationTracker.MODIFICATION_COUNT);
      });

    NonCodeMembersHolder result = map.get(methods);
    if (result == null) {
      map.put(methods, result = new NonCodeMembersHolder(methods, place));
    }
    return result;
  }

  public NonCodeMembersHolder() {
  }

  private NonCodeMembersHolder(List<Map> data, PsiElement place) {
    final PsiManager manager = place.getManager();
    for (Map prop : data) {
      final Object decltype = prop.get("declarationType");
      if (decltype == DeclarationType.CLOSURE) {
        PsiElement closureDescriptor = createClosureDescriptor(prop, place, manager);
        if (closureDescriptor != null) {
          addDeclaration(closureDescriptor);
        }
      }
      else if (decltype == DeclarationType.VARIABLE) {
        addDeclaration(createVariable(prop, place, manager));
      }
      else {
        //declarationType == DeclarationType.METHOD
        final PsiElement method = createMethod(prop, place, manager);
        addDeclaration(method);
      }
    }
  }

  public void addDeclaration(@NotNull PsiElement element) {
    myDeclarations.add(element);
  }

  private static PsiElement createVariable(Map prop, PsiElement place, PsiManager manager) {
    String name = String.valueOf(prop.get("name"));
    final String type = String.valueOf(prop.get("type"));
    return new GrLightVariable(manager, name, type, Collections.<PsiElement>emptyList(), place.getContainingFile());
  }

  @Nullable
  private static PsiElement createClosureDescriptor(Map prop, PsiElement place, PsiManager manager) {
    final ClosureDescriptor closure = new ClosureDescriptor(manager);

    final Object method = prop.get("method");
    if (!(method instanceof Map)) return null;

    closure.setMethod(((Map)method));

//    closure.setReturnType(convertToPsiType(String.valueOf(prop.get("type")), place));
    final Object closureParams = prop.get("params");
    if (closureParams instanceof Map) {
      boolean first = true;
      for (Object paramName : ((Map)closureParams).keySet()) {
        Object value = ((Map)closureParams).get(paramName);
        boolean isNamed = first && value instanceof List;
        first = false;
        String typeName = isNamed ? CommonClassNames.JAVA_UTIL_MAP : String.valueOf(value);
        closure.addParameter(typeName, String.valueOf(paramName));
      }
    }

    Object doc = prop.get("doc");
    if (doc instanceof String) {
      closure.putUserData(DOCUMENTATION, (String)doc);
    }

    Object docUrl = prop.get("docUrl");
    if (docUrl instanceof String) {
      closure.putUserData(DOCUMENTATION_URL, (String)docUrl);
    }


    return closure;
  }

  private static GrLightMethodBuilder createMethod(Map prop, PsiElement place, PsiManager manager) {
    String name = String.valueOf(prop.get("name"));

    final GrLightMethodBuilder method = new GrLightMethodBuilder(manager, name).addModifier(PsiModifier.PUBLIC);

    if (Boolean.TRUE.equals(prop.get("constructor"))) {
      method.setConstructor(true);
    } else {
      method.setReturnType(convertToPsiType(String.valueOf(prop.get("type")), place));
    }

    final Object params = prop.get("params");
    if (params instanceof Map) {
      boolean first = true;
      for (Object paramName : ((Map)params).keySet()) {
        Object value = ((Map)params).get(paramName);
        boolean isNamed = first && value instanceof List;
        first = false;
        String typeName = isNamed ? CommonClassNames.JAVA_UTIL_MAP : String.valueOf(value);
        method.addParameter(String.valueOf(paramName), convertToPsiType(typeName, place), false);

        if (isNamed) {
          Map<String, NamedArgumentDescriptor> namedParams = ContainerUtil.newHashMap();
          for (Object o : (List)value) {
            if (o instanceof CustomMembersGenerator.ParameterDescriptor) {
              namedParams.put(((CustomMembersGenerator.ParameterDescriptor)o).name,
                              ((CustomMembersGenerator.ParameterDescriptor)o).descriptor);
            }
          }
          method.setNamedParameters(namedParams);
        }
      }
    }

    if (Boolean.TRUE.equals(prop.get("isStatic"))) {
      method.addModifier(PsiModifier.STATIC);
    }

    final Object bindsTo = prop.get("bindsTo");
    if (bindsTo instanceof PsiElement) {
      method.setNavigationElement((PsiElement)bindsTo);
    }

    final Object toThrow = prop.get(CustomMembersGenerator.THROWS);
    if (toThrow instanceof List) {
      for (Object o : ((List)toThrow)) {
        final PsiType psiType = convertToPsiType(String.valueOf(o), place);
        if (psiType instanceof PsiClassType) {
          method.addException((PsiClassType)psiType);
        }
      }
    }

    Object doc = prop.get("doc");
    if (doc instanceof String) {
      method.putUserData(DOCUMENTATION, (String)doc);
    }

    Object docUrl = prop.get("docUrl");
    if (docUrl instanceof String) {
      method.putUserData(DOCUMENTATION_URL, (String)docUrl);
    }

    Object qName = prop.get("containingClass");
    if (qName instanceof String) {
      PsiClass foundClass = JavaPsiFacade.getInstance(manager.getProject()).findClass(((String)qName), place.getResolveScope());
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
    for (PsiScopeProcessor each : GroovyResolverProcessor.allProcessors(_processor)) {
      String hint = ResolveUtil.getNameHint(each);
      for (PsiElement declaration : myDeclarations) {
        if (checkName(hint, declaration) && !each.execute(declaration, state)) return false;
      }
    }
    return true;
  }

  private static boolean checkName(String hint, PsiElement declaration) {
    if (hint != null && declaration instanceof PsiNamedElement && !isConstructor(declaration)) {
      return hint.equals(((PsiNamedElement)declaration).getName());
    }
    return true;
  }

  private static boolean isConstructor(PsiElement declaration) {
    return declaration instanceof PsiMethod && ((PsiMethod)declaration).isConstructor();
  }
}
