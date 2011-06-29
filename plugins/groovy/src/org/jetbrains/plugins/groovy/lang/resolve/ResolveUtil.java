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

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.scope.DelegatingScopeProcessor;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceResolveUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getSmartReturnType;

/**
 * @author ven
 */
@SuppressWarnings({"StringBufferReplaceableByString"})
public class ResolveUtil {
  public static final PsiScopeProcessor.Event DECLARATION_SCOPE_PASSED = new PsiScopeProcessor.Event() {};
  private static final Logger LOG = Logger.getInstance(ResolveUtil.class);

  private ResolveUtil() {
  }

  public static boolean treeWalkUp(@NotNull GroovyPsiElement place, PsiScopeProcessor processor, boolean processNonCodeMethods) {
    PsiElement lastParent = null;
    PsiElement run = place;

    final Project project = place.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

    while (run != null) {
      if (!run.processDeclarations(processor, ResolveState.initial(), lastParent, place)) return false;
      if (processNonCodeMethods) {
        if (run instanceof GrTypeDefinition) {
          if (!processNonCodeMembers(factory.createType(((GrTypeDefinition)run)), processor, place)) return false;
        }
        else if ((run instanceof GroovyFileBase) && ((GroovyFileBase)run).isScript()) {
          final PsiClass psiClass = ((GroovyFileBase)run).getScriptClass();
          if (psiClass != null) {
            if (!processNonCodeMembers(factory.createType(psiClass), processor, place)) return false;
          }
        }
        else if (run instanceof GrClosableBlock) {
          if (!categoryIteration((GrClosableBlock)run, processor)) return false;
          if (!withIteration((GrClosableBlock)run, processor, place)) return false;
        }
      }
      lastParent = run;
      run = run.getContext();
      processor.handleEvent(JavaScopeProcessorEvent.CHANGE_LEVEL, null);
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

  @Nullable
  public static String getNameHint(PsiScopeProcessor processor) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    if (nameHint == null) {
      return null;
    }

    return nameHint.getName(ResolveState.initial());
  }

  public static boolean processElement(PsiScopeProcessor processor, PsiNamedElement namedElement, ResolveState state) {
    NameHint nameHint = processor.getHint(NameHint.KEY);
    //todo [DIANA] look more carefully
    String name = nameHint == null ? null : nameHint.getName(state);
    if (name == null || name.equals(namedElement.getName())) {
      return processor.execute(namedElement, state);
    }

    return true;
  }

  public static boolean processAllDeclarations(@NotNull PsiType type,
                                               @NotNull PsiScopeProcessor processor,
                                               @NotNull ResolveState state,
                                               @NotNull GroovyPsiElement place) {
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = resolveResult.getElement();
      final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
      state = state.put(PsiSubstitutor.KEY, substitutor.putAll(resolveResult.getSubstitutor()));
      if (psiClass != null) {
        if (!psiClass.processDeclarations(processor, state, null, place)) return false;
      }
    }
    if (!processNonCodeMembers(type, processor, place, state)) return false;
    if (!processCategoryMembers(place, processor)) return false;
    return true;
  }

  public static boolean processNonCodeMembers(PsiType type,
                                              PsiScopeProcessor processor,
                                              GroovyPsiElement place) {
    return processNonCodeMembers(type, processor, place, ResolveState.initial());
  }

  public static boolean processNonCodeMembers(PsiType type,
                                              PsiScopeProcessor processor,
                                              GroovyPsiElement place,
                                              ResolveState state) {
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).toArrayType();
    }
    if (!NonCodeMembersContributor.runContributors(type, processor, place, state)) {
      return false;
    }

    if (!GroovyDslFileIndex.processExecutors(type, place, processor, state)) {
      return false;
    }

    return true;
  }

  private static final Key<PsiType> COMPARABLE = Key.create(CommonClassNames.JAVA_LANG_COMPARABLE);
  private static final Key<PsiType> SERIALIZABLE = Key.create(CommonClassNames.JAVA_IO_SERIALIZABLE);
  private static final Key<PsiType> STRING = Key.create(CommonClassNames.JAVA_LANG_STRING);

  private static void collectSuperTypes(PsiType type, Map<String, PsiType> visited, Project project) {
    String qName = rawCanonicalText(type);

    if (visited.put(qName, type) != null) {
      return;
    }

    final PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      collectSuperTypes(TypeConversionUtil.erasure(superType), visited, project);
    }

    if (type instanceof PsiArrayType && superTypes.length == 0) {
      PsiType comparable = createTypeFromText(project, COMPARABLE, CommonClassNames.JAVA_LANG_COMPARABLE);
      PsiType serializable = createTypeFromText(project, SERIALIZABLE, CommonClassNames.JAVA_IO_SERIALIZABLE);
      collectSuperTypes(comparable, visited, project);
      collectSuperTypes(serializable, visited, project);
    }

    if (GroovyCommonClassNames.GROOVY_LANG_GSTRING.equals(qName)) {
      collectSuperTypes(createTypeFromText(project, STRING, CommonClassNames.JAVA_LANG_STRING), visited, project);
    }

  }

  public static PsiType createTypeFromText(Project project, Key<PsiType> key, String text) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiType type = project.getUserData(key);
    if (type == null) {
      type = factory.createTypeFromText(text, null);
      project.putUserData(key, type);
    }
    return type;
  }

  public static Map<String, PsiType> getAllSuperTypes(@NotNull PsiType base, final Project project) {
    final Map<String, Map<String, PsiType>> cache =
      CachedValuesManager.getManager(project).getCachedValue(project, new CachedValueProvider<Map<String, Map<String, PsiType>>>() {
        @Override
        public Result<Map<String, Map<String, PsiType>>> compute() {
          final Map<String, Map<String, PsiType>> result = new ConcurrentHashMap<String, Map<String, PsiType>>();
          return Result.create(result, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT, ProjectRootManager.getInstance(project));
        }
      });

    final PsiClass cls = PsiUtil.resolveClassInType(base);
    //noinspection ConstantConditions
    String key;
    if (cls instanceof PsiTypeParameter) {
      final PsiClass superClass = cls.getSuperClass();
      key = cls.getName() + (superClass == null ? CommonClassNames.JAVA_LANG_OBJECT : superClass.getName());
    }
    else {
      key = TypeConversionUtil.erasure(base).getCanonicalText();
    }
    if (key == null) key = "";
    Map<String, PsiType> result = cache.get(key);
    if (result == null) {
      result = new HashMap<String, PsiType>();
      collectSuperTypes(base, result, project);
      cache.put(key, result);
    }
    return result;
  }

  @NotNull
  private static String rawCanonicalText(@NotNull PsiType type) {
    final String result = type.getCanonicalText();
    if (result == null) return "";
    final int i = result.indexOf('<');
    if (i > 0) return result.substring(0, i);
    return result;
  }

  @Nullable
  public static PsiType getListTypeForSpreadOperator(GrReferenceExpression refExpr, PsiType componentType) {
    PsiClass clazz = JavaPsiFacade.getInstance(refExpr.getManager().getProject())
      .findClass(CommonClassNames.JAVA_UTIL_LIST, refExpr.getResolveScope());
    if (clazz != null) {
      PsiTypeParameter[] typeParameters = clazz.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(typeParameters[0], componentType);
        return JavaPsiFacade.getInstance(refExpr.getProject()).getElementFactory().createType(clazz, substitutor);
      }
    }

    return null;
  }

  public static GroovyPsiElement resolveProperty(GroovyPsiElement place, String name) {
    PropertyResolverProcessor processor = new PropertyResolverProcessor(name, place);
    return resolveExistingElement(place, processor, GrVariable.class, GrReferenceExpression.class);
  }

  @Nullable
  public static PsiClass resolveClass(GroovyPsiElement place, String name) {
    ClassResolverProcessor processor = new ClassResolverProcessor(name, place);
    return resolveExistingElement(place, processor, PsiClass.class);
  }

  @Nullable
  public static <T> T resolveExistingElement(GroovyPsiElement place, ResolverProcessor processor, Class<? extends T>... classes) {
    treeWalkUp(place, processor, true);
    final GroovyResolveResult[] candidates = processor.getCandidates();
    for (GroovyResolveResult candidate : candidates) {
      final PsiElement element = candidate.getElement();
      if (element == place) continue;
      for (Class<? extends T> clazz : classes) {
        if (clazz.isInstance(element)) //noinspection unchecked
          return (T)element;
      }
    }

    return null;
  }

  @NotNull
  public static Pair<GrStatement, GrLabeledStatement> resolveLabelTargets(@Nullable String labelName,
                                                                          @Nullable PsiElement element,
                                                                          boolean isBreak) {
    if (element == null) return new Pair<GrStatement, GrLabeledStatement>(null, null);

    if (labelName == null) {
      do {
        element = element.getContext();
        if (element == null || element instanceof GrClosableBlock || element instanceof GrMember || element instanceof GroovyFile) {
          return new Pair<GrStatement, GrLabeledStatement>(null, null);
        }
      }
      while (!(element instanceof GrLoopStatement) && !(isBreak && element instanceof GrSwitchStatement));
      return new Pair<GrStatement, GrLabeledStatement>(((GrStatement)element), null);
    }

    GrStatement statement = null;
    do {
      PsiElement last = element;
      element = element.getContext();
      if (element == null || element instanceof GrMember || element instanceof GroovyFile) break;
      if (element instanceof GrStatement && !(element instanceof GrClosableBlock)) {
        statement = (GrStatement)element;
      }
      PsiElement sibling = element;
      while (sibling != null) {
        final GrLabeledStatement labelStatement = findLabelStatementIn(sibling, last, labelName);
        if (labelStatement != null) {
          return new Pair<GrStatement, GrLabeledStatement>(statement, labelStatement);
        }
        sibling = sibling.getPrevSibling();
      }
      if (element instanceof GrClosableBlock) break;
    }
    while (true);
    return new Pair<GrStatement, GrLabeledStatement>(null, null);
  }

  private static boolean isApplicableLabelStatement(PsiElement element, String labelName) {
    return ((element instanceof GrLabeledStatement && labelName.equals(((GrLabeledStatement)element).getLabelName())));
  }

  @Nullable
  private static GrLabeledStatement findLabelStatementIn(PsiElement element, PsiElement lastChild, String labelName) {
    if (isApplicableLabelStatement(element, labelName)) {
      return (GrLabeledStatement)element;
    }
    for (PsiElement child = element.getFirstChild(); child != null && child != lastChild; child = child.getNextSibling()) {
      final GrLabeledStatement statement = findLabelStatementIn(child, child, labelName);
      if (statement != null) return statement;
    }
    return null;
  }

  @Nullable
  public static GrLabeledStatement resolveLabeledStatement(@Nullable String labelName, @Nullable PsiElement element, boolean isBreak) {
    return resolveLabelTargets(labelName, element, isBreak).second;
  }

  @Nullable
  public static GrStatement resolveLabelTargetStatement(@Nullable String labelName, @Nullable PsiElement element, boolean isBreak) {
    return resolveLabelTargets(labelName, element, isBreak).first;
  }

  public static boolean processCategoryMembers(PsiElement place, PsiScopeProcessor processor) {
    while (place != null) {
      if (place instanceof GrMember) break;
      if (place instanceof GrClosableBlock) {
        if (!categoryIteration((GrClosableBlock)place, processor)) return false;
      }
      place = place.getContext();
    }

    return true;
  }

  private static boolean categoryIteration(GrClosableBlock place, final PsiScopeProcessor processor) {
    final GrMethodCall call = checkMethodCall(place, "use");
    if (call == null) return true;

    final GrClosableBlock[] closures = call.getClosureArguments();
    final GrExpression[] args = call.getExpressionArguments();
    int last = args.length - 1;
    if (!(closures.length == 1 && place.equals(closures[0])) &&
        !(args.length > 0 && place.equals(args[last]))) {
      return true;
    }

    if (!(call.resolveMethod() instanceof GrGdkMethod)) return true;

    final DelegatingScopeProcessor delegate = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(PsiElement element, ResolveState state) {
        if (element instanceof PsiMethod) {
          if (!((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC)) return true;
          if (((PsiMethod)element).getParameterList().getParametersCount() == 0) return true;
          return processor.execute(new GrGdkMethodImpl((PsiMethod)element, false), state);
        }
        else {
          return processor.execute(element, state);
        }
      }
    };
    for (GrExpression arg : args) {
      if (arg instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)arg).resolve();
        if (resolved instanceof PsiClass) {
          if (!resolved.processDeclarations(delegate, ResolveState.initial().put(ResolverProcessor.RESOLVE_CONTEXT, call), null, place)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean withIteration(GrClosableBlock block, final PsiScopeProcessor processor, GroovyPsiElement place) {
    GrMethodCall call = checkMethodCall(block, "with");
    if (call == null) return true;
    final GrExpression invoked = call.getInvokedExpression();
    LOG.assertTrue(invoked instanceof GrReferenceExpression);
    final GrExpression qualifier = ((GrReferenceExpression)invoked).getQualifier();
    if (qualifier == null) return true;
    if (!GrReferenceResolveUtil.processQualifier(processor, qualifier, place)) return false;

    return true;
  }

  @Nullable
  private static GrMethodCall checkMethodCall(GrClosableBlock place, String methodName) {
    final PsiElement context = place.getContext();
    GrMethodCall call = null;
    if (context instanceof GrMethodCall) {
      call = (GrMethodCall)context;
    }
    else if (context instanceof GrArgumentList) {
      final PsiElement ccontext = context.getContext();
      if (ccontext instanceof GrMethodCall) {
        call = (GrMethodCall)ccontext;
      }
    }
    if (call == null) return null;
    final GrExpression invoked = call.getInvokedExpression();
    if (!(invoked instanceof GrReferenceExpression) || !methodName.equals(((GrReferenceExpression)invoked).getReferenceName())) {
      return null;
    }
    return call;
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
                                  PsiSubstitutor substitutor2) {  //method1 has more general parameter types then method2
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

  public static GroovyResolveResult[] getCallVariants(GroovyPsiElement place) {
    final PsiElement parent = place.getParent();
    GroovyResolveResult[] variants = GroovyResolveResult.EMPTY_ARRAY;
    if (parent instanceof GrCallExpression) {
      variants = ((GrCallExpression)parent).getCallVariants(place instanceof GrExpression ? (GrExpression)place : null);
    }
    else if (parent instanceof GrConstructorInvocation) {
      final PsiClass clazz = ((GrConstructorInvocation)parent).getDelegatedClass();
      if (clazz != null) {
        final PsiMethod[] constructors = clazz.getConstructors();
        variants = getConstructorResolveResult(constructors, place);
      }
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      final PsiElement element = ((GrAnonymousClassDefinition)parent).getBaseClassReferenceGroovy().resolve();
      if (element instanceof PsiClass) {
        final PsiMethod[] constructors = ((PsiClass)element).getConstructors();
        variants = getConstructorResolveResult(constructors, place);
      }
    }
    else if (place instanceof GrReferenceExpression) {
      variants = ((GrReferenceExpression)place).getSameNameVariants();
    }
    return variants;
  }

  public static GroovyResolveResult[] getConstructorResolveResult(PsiMethod[] constructors, PsiElement place) {
    GroovyResolveResult[] variants = new GroovyResolveResult[constructors.length];
    for (int i = 0; i < constructors.length; i++) {
      final boolean isAccessible = PsiUtil.isAccessible(constructors[i], place, null);
      variants[i] = new GroovyResolveResultImpl(constructors[i], isAccessible);
    }
    return variants;
  }

  public static GroovyResolveResult[] getNonCodeConstructors(PsiClass psiClass, GroovyPsiElement place, PsiSubstitutor substitutor) {
    final PsiClassType qualifierType = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
    final MethodResolverProcessor processor = new MethodResolverProcessor(psiClass.getName(), place, true, null, null, PsiType.EMPTY_ARRAY);
    NonCodeMembersContributor
      .runContributors(qualifierType, processor, place, ResolveState.initial().put(PsiSubstitutor.KEY, substitutor));
    return processor.getCandidates();
  }

  public static PsiMethod[] getAllClassConstructors(PsiClass psiClass, GroovyPsiElement place, PsiSubstitutor substitutor) {
    final PsiMethod[] realConstructors = psiClass.getConstructors();
    final GroovyResolveResult[] nonCodeConstructors = getNonCodeConstructors(psiClass, place, substitutor);
    PsiMethod[] constructors = new PsiMethod[realConstructors.length + nonCodeConstructors.length];
    System.arraycopy(realConstructors, 0, constructors, 0, realConstructors.length);
    for (int i = 0; i < nonCodeConstructors.length; i++) {
      GroovyResolveResult nonCodeConstructor = nonCodeConstructors[i];
      final PsiElement element = nonCodeConstructor.getElement();
      if (element instanceof PsiMethod) {
        constructors[i + realConstructors.length] = (PsiMethod)element;
      }
    }
    return constructors;
  }

  public static boolean isInUseScope(GroovyResolveResult resolveResult) {
    if (resolveResult != null && resolveResult.getElement() instanceof GrGdkMethod) return false;
    return resolveResult != null && isInUseScope(resolveResult.getCurrentFileResolveContext(), resolveResult.getElement());
  }

  public static boolean isInUseScope(@Nullable PsiElement context, @Nullable PsiElement method) {
    if (method instanceof GrGdkMethod) return false;
    if (context instanceof GrMethodCall && context.isValid()) {
      final GrExpression expression = ((GrMethodCall)context).getInvokedExpression();
      if (expression instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
        if (resolved instanceof GrGdkMethod && "use".equals(((GrGdkMethod)resolved).getStaticMethod().getName())) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean isInWithContext(GroovyPsiElement resolveContext) {
    if (resolveContext instanceof GrExpression) {
      final PsiElement parent = resolveContext.getParent();
      if (parent instanceof GrReferenceExpression && ((GrReferenceExpression)parent).getQualifier() == resolveContext) {
        final PsiElement pparent = parent.getParent();
        if (pparent instanceof GrMethodCall) {
          final PsiMethod method = ((GrMethodCall)pparent).resolveMethod();
          if (method instanceof GrGdkMethod && "with".equals(method.getName())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean isKeyOfMap(GrReferenceExpression ref) {
    if (ref.multiResolve(false).length > 0) return false;
    return mayBeKeyOfMap(ref);
  }

  public static boolean mayBeKeyOfMap(GrReferenceExpression ref) {
    final GrExpression qualifier = getSelfOrWithQualifier(ref);
    if (qualifier == null) return false;
    if (qualifier instanceof GrThisSuperReferenceExpression) return false;
    if (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass) return false;
    return InheritanceUtil.isInheritor(qualifier.getType(), CommonClassNames.JAVA_UTIL_MAP);
  }


  @Nullable
  public static GrExpression getSelfOrWithQualifier(GrReferenceExpression ref) {
    final GrExpression qualifier = ref.getQualifierExpression();
    if (qualifier != null) {
      return qualifier;
    }

    PsiElement place = ref;
    while (true) {
      final GrClosableBlock closure = PsiTreeUtil.getParentOfType(place, GrClosableBlock.class, true, GrMember.class, GroovyFile.class);
      if (closure == null) break;
      place = closure;
      PsiElement clParent = closure.getParent();
      if (clParent instanceof GrArgumentList) clParent = clParent.getParent();
      if (!(clParent instanceof GrMethodCall)) continue;
      final GrExpression expression = ((GrMethodCall)clParent).getInvokedExpression();
      if (expression instanceof GrReferenceExpression &&
          "with".equals(((GrReferenceExpression)expression).getReferenceName()) &&
          ((GrReferenceExpression)expression).resolve() instanceof GrGdkMethod) {
        final GrExpression withQualifier = ((GrReferenceExpression)expression).getQualifierExpression();
        if (withQualifier != null) {
          return withQualifier;
        }
      }
    }
    return null;
  }


  @NotNull
  public static GroovyResolveResult[] getMethodCandidates(@NotNull PsiType thisType,
                                                          @Nullable String methodName,
                                                          @NotNull GroovyPsiElement place,
                                                          @Nullable PsiType... argumentTypes) {
    return getMethodCandidates(thisType, methodName, place, true, argumentTypes);
  }

  @NotNull
  public static GroovyResolveResult[] getMethodCandidates(@NotNull PsiType thisType,
                                                          @Nullable String methodName,
                                                          @NotNull GroovyPsiElement place,
                                                          boolean resolveClosures,
                                                          @Nullable PsiType... argumentTypes) {
    if (methodName == null) return GroovyResolveResult.EMPTY_ARRAY;

    MethodResolverProcessor processor =
      new MethodResolverProcessor(methodName, place, false, thisType, argumentTypes, PsiType.EMPTY_ARRAY);
    processAllDeclarations(thisType, processor, ResolveState.initial(), place);
    boolean hasApplicableMethods = processor.hasApplicableCandidates();
    final GroovyResolveResult[] methodCandidates = processor.getCandidates();
    if (hasApplicableMethods && methodCandidates.length == 1) return methodCandidates;

    final GroovyResolveResult[] allPropertyCandidates;
    if (resolveClosures) {
      PropertyResolverProcessor propertyResolver = new PropertyResolverProcessor(methodName, place);
      processAllDeclarations(thisType, propertyResolver, ResolveState.initial(), place);
      allPropertyCandidates = propertyResolver.getCandidates();
    }
    else {
      allPropertyCandidates = GroovyResolveResult.EMPTY_ARRAY;
    }

    List<GroovyResolveResult> propertyCandidates = new ArrayList<GroovyResolveResult>(allPropertyCandidates.length);
    for (GroovyResolveResult candidate : allPropertyCandidates) {
      final PsiElement resolved = candidate.getElement();
      if (!(resolved instanceof GrField)) continue;
      final PsiType type = ((GrField)resolved).getTypeGroovy();
      if (isApplicableClosureType(type, argumentTypes, place)) {
        propertyCandidates.add(candidate);
      }
    }

    for (GroovyResolveResult candidate : propertyCandidates) {
      final PsiElement element = candidate.getElement();
      if (element instanceof GrField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiTreeUtil.isContextAncestor(containingClass, place, true)) {
          return new GroovyResolveResult[]{candidate};
        }
      }
    }

    List<GroovyResolveResult> allCandidates = new ArrayList<GroovyResolveResult>();
    if (hasApplicableMethods) {
      ContainerUtil.addAll(allCandidates, methodCandidates);
    }
    ContainerUtil.addAll(allCandidates, propertyCandidates);

    //search for getters
    for (String getterName : GroovyPropertyUtils.suggestGettersName(methodName)) {
      AccessorResolverProcessor getterResolver = new AccessorResolverProcessor(getterName, place, true);
      processAllDeclarations(thisType, getterResolver, ResolveState.initial(), place);
      final GroovyResolveResult[] candidates = getterResolver.getCandidates(); //can be only one candidate
      final List<GroovyResolveResult> applicable = new ArrayList<GroovyResolveResult>();
      for (GroovyResolveResult candidate : candidates) {
        final PsiType type = getSmartReturnType((PsiMethod)candidate.getElement());
        if (isApplicableClosureType(type, argumentTypes, place)) {
          applicable.add(candidate);
        }
      }
      if (applicable.size() == 1) {
        return applicable.toArray(new GroovyResolveResult[applicable.size()]);
      }
      ContainerUtil.addAll(allCandidates, applicable);
    }

    if (allCandidates.size() > 0) {
      return allCandidates.toArray(new GroovyResolveResult[allCandidates.size()]);
    }
    else if (!hasApplicableMethods) {
      return methodCandidates;
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static boolean isApplicableClosureType(PsiType type, PsiType[] argTypes, GroovyPsiElement place) {
    if (!(type instanceof GrClosureType)) return false;

    final GrClosureSignature signature = ((GrClosureType)type).getSignature();
    return GrClosureSignatureUtil.isSignatureApplicable(signature, argTypes, place);
  }

  @Nullable
  public static PsiType extractReturnTypeFromCandidate(GroovyResolveResult candidate) {
    final PsiElement element = candidate.getElement();
    if (element instanceof PsiMethod && !candidate.isInvokedOnProperty()) {
      return candidate.getSubstitutor().substitute(getSmartReturnType((PsiMethod)element));
    }

    final PsiType type;
    if (element instanceof GrField) {
      type = ((GrField)element).getTypeGroovy();
    }
    else if (element instanceof PsiMethod) {
      type = getSmartReturnType((PsiMethod)element);
    }
    else {
      return null;
    }
    if (type instanceof GrClosureType) {
      return candidate.getSubstitutor().substitute(((GrClosureType)type).getSignature().getReturnType());
    }
    return null;
  }
}
