/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.*;

/**
 * @author ven
 */
@SuppressWarnings({"StringBufferReplaceableByString"})
public class ResolveUtil {
  public static final PsiScopeProcessor.Event DECLARATION_SCOPE_PASSED = new PsiScopeProcessor.Event() {
  };

  public static boolean treeWalkUp(PsiElement place, PsiScopeProcessor processor) {
    PsiElement lastParent = null;
    PsiElement run = place;
    while (run != null) {
      if (!run.processDeclarations(processor, ResolveState.initial(), lastParent, place)) return false;
      lastParent = run;
      run = run.getContext();
    }

    return true;
  }

  public static boolean processChildren(PsiElement element,
                                        PsiScopeProcessor processor,
                                        ResolveState substitutor,
                                        PsiElement lastParent,
                                        PsiElement place) {
    PsiElement run = lastParent == null ? element.getLastChild() : lastParent.getPrevSibling();
    while (run != null) {
      if (!run.processDeclarations(processor, substitutor, null, place)) return false;
      run = run.getPrevSibling();
    }

    return true;
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    //todo [DIANA] look more carefully
    String name = nameHint == null ? null : nameHint.getName(ResolveState.initial());
    if (name == null || name.equals(namedElement.getName())) {
      return processor.execute(namedElement, ResolveState.initial());
    }

    return true;
  }

  public static boolean processNonCodeMethods(PsiType type,
                                              PsiScopeProcessor processor,
                                              Project project,
                                              PsiElement place,
                                              boolean forCompletion) {
    return processNonCodeMethods(type, processor, project, new HashSet<String>(), place, forCompletion);
  }

  private static boolean processNonCodeMethods(PsiType type,
                                               PsiScopeProcessor processor,
                                               Project project,
                                               Set<String> visited,
                                               PsiElement place,
                                               boolean forCompletion) {
    String qName = rawCanonicalText(type);

    if (qName != null) {
      if (visited.contains(qName)) return true;
      visited.add(qName);
      for (PsiMethod defaultMethod : GroovyPsiManager.getInstance(project).getDefaultMethods(qName)) {
        if (!processElement(processor, defaultMethod)) return false;
      }

      for (PsiMethod method : DynamicManager.getInstance(project).getMethods(qName)) {
        if (!processElement(processor, method)) return false;
      }

      for (PsiVariable var : DynamicManager.getInstance(project).getProperties(qName)) {
        if (!processElement(processor, var)) return false;
      }

      for (NonCodeMembersProcessor membersProcessor : NonCodeMembersProcessor.EP_NAME.getExtensions()) {
        if (!membersProcessor.processNonCodeMethods(type, processor, place, forCompletion)) return false;
      }
      /*
      for (PsiMethod method : getDomainClassMethods(type, project)) {
        if (!processElement(processor, method)) return false;
      }
      */

      if (type instanceof PsiArrayType) {
        //implicit super types
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiClassType t = factory.createTypeByFQClassName("java.lang.Object", GlobalSearchScope.allScope(project));
        if (!processNonCodeMethods(t, processor, project, visited, place, forCompletion)) return false;
        t = factory.createTypeByFQClassName("java.lang.Comparable", GlobalSearchScope.allScope(project));
        if (!processNonCodeMethods(t, processor, project, visited, place, forCompletion)) return false;
        t = factory.createTypeByFQClassName("java.io.Serializable", GlobalSearchScope.allScope(project));
        if (!processNonCodeMethods(t, processor, project, visited, place, forCompletion)) return false;
      }
      else {
        for (PsiType superType : type.getSuperTypes()) {
          if (!processNonCodeMethods(TypeConversionUtil.erasure(superType), processor, project, visited, place, forCompletion)) {
            return false;
          }
        }
      }
    }

    return true;
  }

  /*
  private static List<PsiMethod> getDomainClassMethods(PsiType type, Project project) {
    if (!(type instanceof PsiClassType)) {
      return Collections.emptyList();
    }

    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(type.getCanonicalText(), GlobalSearchScope.allScope(project));
    if (psiClass == null || !DomainClassUtils.isDomainClass(psiClass)) {
      return Collections.emptyList();
    }

    List<PsiMethod> res = new ArrayList<PsiMethod>();

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    final PsiField[] fields = DomainClassUtils.getDomainFields(psiClass);

    for (PsiField field : fields) {
      res.add(factory.createMethodFromText("List " + DOMAIN_LIST_ORDER + capitalize(field.getName()) + "()", null));
    }

    List<PsiMethod> start = new ArrayList<PsiMethod>(FINDER_PREFICES.length);
    for (String prefix : FINDER_PREFICES) {
      start.add(factory.createMethodFromText("List " + prefix + "()", null));
    }

    for (int i = 0; i < fields.length; i++) {

      res.addAll(getNewMethods(start, fields, factory));
    }

    return res;
  }


  private static List<PsiMethod> getNewMethods(List<PsiMethod> methods, PsiField[] fields, PsiElementFactory factory) {
    List<PsiMethod> res = new ArrayList<PsiMethod>(methods.size());
    for (PsiMethod method : methods) {
      for (PsiField field : fields) {
        if (isUsed(method, field)) continue;
        for (String connective : DOMAIN_CONNECTIVES) {
          //with One-Parameter-Postfix
          for (String expr : DOMAIN_FINDER_EXPRESSIONS_WITH_ONE_PARAMETER) {
            StringBuffer methodText = getMethodName(method, field, connective).append(expr).append('(');
            addExistingParameter(method, methodText);
            addParameter(methodText, field, field.getName());
            methodText.delete(methodText.length() - 1, methodText.length()).append(')');

            res.add(factory.createMethodFromText(methodText.toString(), null));
          }

          {
            //without postfix
            StringBuffer methodText = getMethodName(method, field, connective).append('(');
            addExistingParameter(method, methodText);
            methodText.delete(methodText.length() - 1, methodText.length()).append(')');

            res.add(factory.createMethodFromText(methodText.toString(), null));
          }

          {
            //with Two-Paramter-Postfix
            StringBuffer methodText = getMethodName(method, field, connective).append("Between").append('(');
            addExistingParameter(method, methodText);
            addParameter(methodText, field, "lower" + capitalize(field.getName()));
            addParameter(methodText, field, "upper" + capitalize(field.getName()));
            methodText.delete(methodText.length() - 1, methodText.length()).append(')');

            res.add(factory.createMethodFromText(methodText.toString(), null));
          }
        }
      }
    }
    return res;
  }

  private static void addExistingParameter(PsiMethod method, StringBuffer methodText) {
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      addParameter(methodText, parameter, parameter.getName());
    }
    if (method.getParameterList().getParameters().length==0) methodText.append(',');
  }

  private static void addParameter(StringBuffer methodText, PsiVariable parameter, String parameterName) {
    methodText.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(",");
  }

  private static StringBuffer getMethodName(PsiMethod method, PsiField field, String connective) {
    return new StringBuffer("List ").append(method.getName()).append(connective).append(capitalize(field.getName()));
  }

  private static boolean isUsed(PsiMethod method, PsiField field) {
    return (method.getName().contains(capitalize(field.getName())));
  }

  */
  private static String rawCanonicalText(PsiType type) {
    final String result = type.getCanonicalText();
    if (result == null) return null;
    final int i = result.indexOf('<');
    if (i > 0) return result.substring(0, i);
    return result;
  }

  public static PsiType getListTypeForSpreadOperator(GrReferenceExpression refExpr, PsiType componentType) {
    PsiClass clazz = findListClass(refExpr.getManager(), refExpr.getResolveScope());
    if (clazz != null) {
      PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(typeParameters[0], componentType);
        return JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory().createType(clazz, substitutor);
      }
    }

    return null;
  }

  public static PsiClass findListClass(PsiManager manager, GlobalSearchScope resolveScope) {
    return JavaPsiFacade.getInstance(manager.getProject()).findClass("java.util.List", resolveScope);
  }

  public static GroovyPsiElement resolveProperty(GroovyPsiElement place, String name) {
    PropertyResolverProcessor processor = new PropertyResolverProcessor(name, place);
    return resolveExistingElement(place, processor, GrVariable.class, GrReferenceExpression.class);
  }

  public static PsiClass resolveClass(GroovyPsiElement place, String name) {
    ClassResolverProcessor processor = new ClassResolverProcessor(name, place);
    return resolveExistingElement(place, processor, PsiClass.class);
  }

  public static <T> T resolveExistingElement(GroovyPsiElement place, ResolverProcessor processor, Class<? extends T>... classes) {
    treeWalkUp(place, processor);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element == place) continue;
      for (Class<? extends T> clazz : classes) {
        if (clazz.isInstance(element)) return (T)element;
      }
    }

    return null;
  }

  public static GrLabeledStatement resolveLabeledStatement(String label, PsiElement place) {
    while (place != null) {
      PsiElement run = place;
      while (run != null) {
        if (run instanceof GrLabeledStatement && label.equals(((GrLabeledStatement)run).getLabel())) return (GrLabeledStatement)run;

        run = run.getPrevSibling();
      }

      place = place.getContext();

      if (place instanceof GrMember || place instanceof GrClosableBlock) break;
    }
    return null;
  }

  public static boolean processCategoryMembers(PsiElement place, ResolverProcessor processor, PsiClassType thisType) {
    PsiElement prev = null;
    while (place != null) {
      if (place instanceof GrMember) break;

      if (place instanceof GrMethodCallExpression) {
        final GrMethodCallExpression call = (GrMethodCallExpression)place;
        final GrExpression invoked = call.getInvokedExpression();
        if (invoked instanceof GrReferenceExpression && "use".equals(((GrReferenceExpression)invoked).getReferenceName())) {
          final GrClosableBlock[] closures = call.getClosureArguments();
          if (closures.length == 1 && closures[0].equals(prev)) {
            if (useCategoryClass(call)) {
              final GrArgumentList argList = call.getArgumentList();
              if (argList != null) {
                final GrExpression[] args = argList.getExpressionArguments();
                if (args.length == 1 && args[0] instanceof GrReferenceExpression) {
                  final PsiElement resolved = ((GrReferenceExpression)args[0]).resolve();
                  if (resolved instanceof PsiClass) {
                    try {
                      processor.setCurrentFileResolveContext(call);
                      if (!resolved.processDeclarations(processor, ResolveState.initial(), null, place)) return false;
                    }
                    finally {
                      processor.setCurrentFileResolveContext(null);
                    }
                  }
                }
              }
            }
          }
        }
      }

      prev = place;
      place = place.getContext();
    }

    return true;
  }

  private static boolean useCategoryClass(GrMethodCallExpression call) {

    final PsiMethod resolved = call.resolveMethod();
    if (resolved instanceof GrGdkMethod) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(call.getProject()).getElementFactory();
      final GlobalSearchScope scope = call.getResolveScope();
      final PsiType[] parametersType =
        {factory.createTypeByFQClassName("java.lang.Class", scope), factory.createTypeByFQClassName("groovy.lang.Closure", scope)};
      final MethodSignature pattern =
        MethodSignatureUtil.createMethodSignature("use", parametersType, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
      return resolved.getSignature(PsiSubstitutor.EMPTY).equals(pattern);
    }

    return false;
  }

  public static PsiElement[] mapToElements(GroovyResolveResult[] candidates) {
    PsiElement[] elements = new PsiElement[candidates.length];
    for (int i = 0; i < elements.length; i++) {
      elements[i] = candidates[i].getElement();
    }

    return elements;
  }

  public static GroovyResolveResult[] filterSameSignatureCandidates(Collection<GroovyResolveResult> candidates) {
    GroovyResolveResult[] array = candidates.toArray(new GroovyResolveResult[candidates.size()]);
    if (array.length == 1) return array;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    result.add(array[0]);

    Outer:
    for (int i = 1; i < array.length; i++) {
      PsiElement currentElement = array[i].getElement();
      if (currentElement instanceof PsiMethod) {
        PsiMethod currentMethod = (PsiMethod)currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          PsiElement element = otherResolveResult.getElement();
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod)element;
            if (dominated(currentMethod, array[i].getSubstitutor(), method, otherResolveResult.getSubstitutor())) {
              continue Outer;
            }
            else if (dominated(method, otherResolveResult.getSubstitutor(), currentMethod, array[i].getSubstitutor())) {
              iterator.remove();
            }
          }
        }
      }

      result.add(array[i]);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  public static boolean dominated(PsiMethod method1,
                                  PsiSubstitutor substitutor1,
                                  PsiMethod method2,
                                  PsiSubstitutor substitutor2) {  //method1 has more general parameter types thn method2
    if (!method1.getName().equals(method2.getName())) return false;

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (params1.length != params2.length) return false;

    for (int i = 0; i < params2.length; i++) {
      PsiType type1 = substitutor1.substitute(params1[i].getType());
      PsiType type2 = substitutor2.substitute(params2[i].getType());
      if (!type1.equals(type2)) return false;
    }

    return true;
  }
}
