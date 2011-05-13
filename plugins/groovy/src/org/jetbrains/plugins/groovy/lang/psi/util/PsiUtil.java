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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.JavaIdentifier;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessor;

import java.util.*;

/**
 * @author ven
 */
@SuppressWarnings({"StaticFieldReferencedViaSubclass"})
public class PsiUtil {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil");
  public static final Key<JavaIdentifier> NAME_IDENTIFIER = new Key<JavaIdentifier>("Java Identifier");

  private PsiUtil() {
  }

  @Nullable
  public static String getQualifiedReferenceText(GrCodeReferenceElement referenceElement) {
    StringBuilder builder = new StringBuilder();
    if (!appendName(referenceElement, builder)) return null;

    return builder.toString();
  }

  private static boolean appendName(GrCodeReferenceElement referenceElement, StringBuilder builder) {
    String refName = referenceElement.getReferenceName();
    if (refName == null) return false;
    GrCodeReferenceElement qualifier = referenceElement.getQualifier();
    if (qualifier != null) {
      appendName(qualifier, builder);
      builder.append(".");
    }

    builder.append(refName);
    return true;
  }

  public static boolean isLValue(GroovyPsiElement element) {
    if (element instanceof GrExpression) {
      PsiElement parent = PsiTreeUtil.skipParentsOfType(element, GrParenthesizedExpression.class);
      if (parent instanceof GrTupleExpression) {
        return isLValue((GroovyPsiElement)parent);
      }
      return parent instanceof GrAssignmentExpression && PsiTreeUtil.isAncestor(((GrAssignmentExpression)parent).getLValue(), element, false);
    }
    return false;
  }

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes,
                                     PsiMethod method,
                                     PsiSubstitutor substitutor,
                                     boolean isInUseCategory, GroovyPsiElement place, final boolean eraseParameterTypes) {
    if (argumentTypes == null) return true;

    GrClosureSignature signature = eraseParameterTypes
                                   ? GrClosureSignatureUtil.createSignatureWithErasedParameterTypes(method) : GrClosureSignatureUtil.createSignature(method, substitutor);
    if (isInUseCategory && method.hasModifierProperty(PsiModifier.STATIC) && method.getParameterList().getParametersCount() > 0) {
      signature = GrClosureSignatureUtil.removeParam(signature, 0);
    }

    //check for default constructor
    if (method.isConstructor()) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length == 0 && argumentTypes.length == 1) {
        return InheritanceUtil.isInheritor(argumentTypes[0], CommonClassNames.JAVA_UTIL_MAP);
      }
      if (parameters.length == 1 &&
          argumentTypes.length == 0 &&
          InheritanceUtil.isInheritor(parameters[0].getType(), CommonClassNames.JAVA_UTIL_MAP)) {
        return false;
      }
    }
    LOG.assertTrue(signature != null);
    if (GrClosureSignatureUtil.isSignatureApplicable(signature, argumentTypes, place)) {
      return true;
    }

    if (method instanceof GrBuilderMethod &&
        !((GrBuilderMethod)method).hasObligatoryNamedArguments()) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0 && parameters[0].getType() instanceof GrMapType &&
          (argumentTypes.length == 0 || !(argumentTypes[0] instanceof GrMapType))) {
        return GrClosureSignatureUtil.isSignatureApplicable(GrClosureSignatureUtil.removeParam(signature, 0), argumentTypes, place);
      }
    }
    return false;
  }

  public static boolean isApplicable(@Nullable PsiType[] argumentTypes, GrClosureType type, GroovyPsiElement context) {
    if (argumentTypes == null) return true;

    GrClosureSignature signature = type.getSignature();
    return GrClosureSignatureUtil.isSignatureApplicable(signature, argumentTypes, context);
  }

  public static PsiClassType createMapType(GlobalSearchScope scope) {
    return new GrMapType(scope);
  }

  @Nullable
  public static GrArgumentList getArgumentsList(PsiElement methodRef) {
    if (methodRef instanceof GrEnumConstant) return ((GrEnumConstant)methodRef).getArgumentList();
    PsiElement parent = methodRef.getParent();
    if (parent instanceof GrCall) {
      return ((GrCall)parent).getArgumentList();
    }
    if (parent instanceof GrAnonymousClassDefinition) {
      return ((GrAnonymousClassDefinition)parent).getArgumentListGroovy();
    }
    return null;
  }

  @Nullable
  public static PsiType[] getArgumentTypes(PsiElement place, boolean nullAsBottom) {
    return getArgumentTypes(place, nullAsBottom, null);
  }
  @Nullable
  public static PsiType[] getArgumentTypes(PsiElement place, boolean nullAsBottom, @Nullable GrExpression stopAt) {
    PsiElement parent = place.getParent();
    if (parent instanceof GrCall) {
      GrCall call = (GrCall)parent;
      GrNamedArgument[] namedArgs = call.getNamedArguments();
      GrExpression[] expressions = call.getExpressionArguments();
      GrClosableBlock[] closures = call.getClosureArguments();

      return getArgumentTypes(namedArgs, expressions, closures, nullAsBottom, stopAt);
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      final GrArgumentList argList = ((GrAnonymousClassDefinition)parent).getArgumentListGroovy();
      return getArgumentTypes(argList, nullAsBottom, stopAt);
    }

    return null;
  }

  public static PsiType[] getArgumentTypes(GrArgumentList argList, boolean nullAsBottom, @Nullable GrExpression stopAt) {
    return getArgumentTypes(argList.getNamedArguments(), argList.getExpressionArguments(), GrClosableBlock.EMPTY_ARRAY, nullAsBottom,
                            stopAt);
  }

  public static PsiType[] getArgumentTypes(GrNamedArgument[] namedArgs,
                                            GrExpression[] expressions,
                                            GrClosableBlock[] closures,
                                            boolean nullAsBottom, @Nullable GrExpression stopAt) {
    List<PsiType> result = new ArrayList<PsiType>();

    if (namedArgs.length > 0) {
      result.add(createMapType(namedArgs[0].getResolveScope()));
    }

    for (GrExpression expression : expressions) {
      PsiType type = expression.getType();
      if (type == null) {
        result.add(nullAsBottom ? PsiType.NULL : TypesUtil.getJavaLangObject(expression));
      } else {
        result.add(type);
      }
      if (stopAt == expression) {
        return result.toArray(new PsiType[result.size()]);
      }
    }

    for (GrClosableBlock closure : closures) {
      PsiType closureType = closure.getType();
      if (closureType != null) {
        result.add(closureType);
      }
      if (stopAt == closure) {
        break;
      }
    }

    return result.toArray(new PsiType[result.size()]);
  }

  public static SearchScope restrictScopeToGroovyFiles(SearchScope originalScope) {
    if (originalScope instanceof GlobalSearchScope) {
      return GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)originalScope, GroovyFileTypeLoader.getGroovyEnabledFileTypes());
    }
    return originalScope;
  }

  @Nullable
  public static PsiClass getJavaLangClass(PsiElement resolved, GlobalSearchScope scope) {
    return JavaPsiFacade.getInstance(resolved.getProject()).findClass(CommonClassNames.JAVA_LANG_CLASS, scope);
  }

  public static boolean isValidReferenceName(String text) {
    final GroovyLexer lexer = new GroovyLexer();
    lexer.start(text);
    return TokenSets.REFERENCE_NAMES_WITHOUT_NUMBERS.contains(lexer.getTokenType()) && lexer.getTokenEnd() == text.length();
  }

  public static void shortenReferences(GroovyPsiElement element) {
    final TextRange textRange = element.getTextRange();
    doShorten(element, textRange.getStartOffset(), textRange.getEndOffset());
  }

  public static void shortenReferences(GroovyPsiElement element, int start, int end) {
    doShorten(element, start, end);
  }

  private static void doShorten(PsiElement element, int start, int end) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      final TextRange range = child.getTextRange();

      if (start < range.getEndOffset() && range.getStartOffset() < end) {
        if (child instanceof GrQualifiedReference) {
          shortenReference((GrQualifiedReference)child);
        }

        doShorten(child, start, end);
      }
      child = child.getNextSibling();
    }
  }


  public static <Qualifier extends PsiElement> boolean shortenReference(GrQualifiedReference<Qualifier> ref) {
    final Qualifier qualifier = ref.getQualifier();
    if (qualifier == null || qualifier instanceof GrSuperReferenceExpression || cannotShortenInContext(ref)) {
      return false;
    }
    if (!canShorten(qualifier)) return false;

    final PsiElement resolved = ref.resolve();
    if (resolved == null) return false;

    final GrQualifiedReference<Qualifier> copy = getCopy(ref);

    copy.setQualifier(null);
    if (!copy.isReferenceTo(resolved)) {
      if (resolved instanceof PsiClass) {
        final GroovyFileBase file = (GroovyFileBase)ref.getContainingFile();
        final PsiClass clazz = (PsiClass)resolved;
        final String qName = clazz.getQualifiedName();
        if (qName != null) {
          if (mayInsertImport(ref)) {
            final GrImportStatement added = file.addImportForClass(clazz);
            if (!copy.isReferenceTo(resolved)) {
              file.removeImport(added);
              return false;
            }
          }
        }
      }
      else {
        return false;
      }
    }
    ref.setQualifier(null);
    return true;
  }

  private static <Qualifier extends PsiElement> GrQualifiedReference<Qualifier> getCopy(GrQualifiedReference<Qualifier> ref) {
    if (ref.getParent() instanceof GrMethodCall) {
      final GrMethodCall copy = ((GrMethodCall)ref.getParent().copy());
      return (GrQualifiedReference<Qualifier>)copy.getInvokedExpression();
    }
    return (GrQualifiedReference<Qualifier>)ref.copy();
  }

  private static <Qualifier extends PsiElement> boolean canShorten(Qualifier qualifier) {
    if (qualifier instanceof GrCodeReferenceElement) return true;
    if (qualifier instanceof GrExpression) {
      if (qualifier instanceof GrThisReferenceExpression) return true;
      if (seemsToBeQualifiedClassName((GrExpression)qualifier)) {
        final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass || resolved instanceof PsiPackage) return true;
      }
    }
    return false;
  }

  private static <Qualifier extends PsiElement> boolean cannotShortenInContext(GrQualifiedReference<Qualifier> ref) {
    return (PsiTreeUtil.getParentOfType(ref, GrDocMemberReference.class) == null &&
            PsiTreeUtil.getParentOfType(ref, GrDocComment.class) != null) ||
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) != null ||
           PsiTreeUtil.getParentOfType(ref, GroovyCodeFragment.class) != null;
  }

  private static <Qualifier extends PsiElement> boolean mayInsertImport(GrQualifiedReference<Qualifier> ref) {
    return PsiTreeUtil.getParentOfType(ref, GrDocComment.class) == null &&
           !(ref.getContainingFile() instanceof GroovyCodeFragment) &&
           PsiTreeUtil.getParentOfType(ref, GrImportStatement.class) == null;
  }

  @Nullable
  public static GrTopLevelDefinition findPreviousTopLevelElementByThisElement(PsiElement element) {
    PsiElement parent = element.getParent();

    while (parent != null && !(parent instanceof GrTopLevelDefinition)) {
      parent = parent.getParent();
    }

    if (parent == null) return null;
    return ((GrTopLevelDefinition)parent);
  }

  public static boolean isStaticsOK(PsiModifierListOwner owner, PsiElement place) {
    return isStaticsOK(owner, place, null);
  }

  public static boolean isStaticsOK(PsiModifierListOwner owner, PsiElement place, @Nullable PsiElement resolveContext) {
    if (owner instanceof PsiMember) {
      if (place instanceof GrReferenceExpression) {
        GrExpression qualifier = ((GrReferenceExpression)place).getQualifierExpression();
        if (qualifier != null) {
          PsiClass containingClass = ((PsiMember)owner).getContainingClass();
          final boolean isStatic = owner.hasModifierProperty(PsiModifier.STATIC) && !(ResolveUtil.isInUseScope(resolveContext));
          if (qualifier instanceof GrReferenceExpression) {
            if ("class".equals(((GrReferenceExpression)qualifier).getReferenceName())) {
              //invoke static members of class from A.class.foo()
              final PsiType type = qualifier.getType();
              if (type instanceof PsiClassType) {
                final PsiClass psiClass = ((PsiClassType)type).resolve();
                if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
                  final PsiType[] params = ((PsiClassType)type).getParameters();
                  if (params.length == 1 && params[0] instanceof PsiClassType) {
                    if (place.getManager().areElementsEquivalent(containingClass, ((PsiClassType)params[0]).resolve())) {
                      return owner.hasModifierProperty(GrModifier.STATIC);
                    }
                  }
                }
              }

            }
            PsiElement qualifierResolved = ((GrReferenceExpression)qualifier).resolve();
            if (qualifierResolved instanceof PsiClass || qualifierResolved instanceof PsiPackage) { //static context
              if (owner instanceof PsiClass) {
                return true;
              }

              //non-physical method, e.g. gdk
              if (containingClass == null) {
                return true;
              }

              if (isStatic) {
                return true;
              }

              //members from java.lang.Class can be invoked without ".class"
              final String qname = containingClass.getQualifiedName();
              if (qname != null && qname.startsWith("java.")) {
                if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname) || CommonClassNames.JAVA_LANG_CLASS.equals(qname)) {
                  return true;
                }

                if (containingClass.isInterface()) {
                  PsiClass javaLangClass =
                    JavaPsiFacade.getInstance(place.getProject()).findClass(CommonClassNames.JAVA_LANG_CLASS, place.getResolveScope());
                  if (javaLangClass != null && javaLangClass.isInheritor(containingClass, true)) {
                    return true;
                  }
                }
              }

              return false;
            }
          }
          else if (qualifier instanceof GrThisReferenceExpression && ((GrThisReferenceExpression)qualifier).getQualifier() == null) {
            //static members may be invoked from this.<...>
            final boolean isInStatic = isInStaticContext((GrThisReferenceExpression)qualifier);
            if (containingClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(containingClass.getQualifiedName())) {
              return !(owner.hasModifierProperty(GrModifier.STATIC) && !CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE);
            }
            else if (isInStatic) return owner.hasModifierProperty(GrModifier.STATIC);
          }

          //instance context
          if (owner instanceof PsiClass) {
            return false;
          }
          return !(isStatic && !CodeInsightSettings.getInstance().SHOW_STATIC_AFTER_INSTANCE);
        }
        else {
          if (((PsiMember)owner).getContainingClass() == null) return true;
          if (owner instanceof GrVariable && !(owner instanceof GrField)) return true;
          if (owner.hasModifierProperty(GrModifier.STATIC)) return true;

          PsiElement stopAt = resolveContext != null ? PsiTreeUtil.findCommonParent(place, resolveContext) : null;
          while (place != null && place != stopAt && !(place instanceof GrMember)) {
            if (place instanceof PsiFile) break;
            place = place.getParent();
          }
          if (place == null || place instanceof PsiFile || place == stopAt) return true;
          if (place instanceof GrTypeDefinition) {
            return !(((GrTypeDefinition)place).hasModifierProperty(GrModifier.STATIC) ||
                     ((GrTypeDefinition)place).getContainingClass() == null);
          }
          return !((GrMember)place).hasModifierProperty(GrModifier.STATIC);
        }
      }
    }
    return true;
  }

  public static boolean isAccessible(PsiElement place, PsiMember member) {

    if (PsiTreeUtil.getParentOfType(place, GrDocComment.class) != null) return true;
    if (member instanceof LightElement) {
      return true;
    }

    if (place instanceof GrReferenceExpression && ((GrReferenceExpression)place).getQualifierExpression() == null) {
      if (member.getContainingClass() instanceof GroovyScriptClass) { //calling toplevel script members from the same script file
        return true;
      }
    }
    return com.intellij.psi.util.PsiUtil.isAccessible(member, place, null);
  }

  public static void reformatCode(final PsiElement element) {
    final TextRange textRange = element.getTextRange();

    PsiFile file = element.getContainingFile();
    FileViewProvider viewProvider = file.getViewProvider();

    if (viewProvider instanceof MultiplePsiFilesPerDocumentFileViewProvider) {
      MultiplePsiFilesPerDocumentFileViewProvider multiProvider = (MultiplePsiFilesPerDocumentFileViewProvider)viewProvider;
      file = multiProvider.getPsi(multiProvider.getBaseLanguage());
    }

    try {
      CodeStyleManager.getInstance(element.getProject())
        .reformatText(file, textRange.getStartOffset(), textRange.getEndOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public static boolean isInStaticContext(GrQualifiedReference refExpression) {
    return isInStaticContext(refExpression, null);
  }

  public static boolean isInStaticContext(GrQualifiedReference refExpression, @Nullable PsiClass targetClass) {
    if (refExpression.getQualifier() != null) {
      PsiElement qualifier = refExpression.getQualifier();
      if (qualifier instanceof GrReferenceExpression) return ((GrReferenceExpression)qualifier).resolve() instanceof PsiClass;
    }
    else {
      PsiElement run = refExpression;
      while (run != null && run != targetClass) {
        if (run instanceof PsiModifierListOwner && ((PsiModifierListOwner)run).hasModifierProperty(PsiModifier.STATIC)) return true;
        run = run.getParent();
      }
    }
    return false;

  }

  public static Iterable<PsiClass> iterateSupers(final @NotNull PsiClass psiClass, final boolean includeSelf) {
    return new Iterable<PsiClass>() {
      public Iterator<PsiClass> iterator() {
        return new Iterator<PsiClass>() {
          TIntStack indices = new TIntStack();
          Stack<PsiClassType[]> superTypesStack = new Stack<PsiClassType[]>();
          PsiClass current;
          boolean nextObtained;
          Set<PsiClass> visited = new HashSet<PsiClass>();

          {
            if (includeSelf) {
              current = psiClass;
              nextObtained = true;
            } else {
              current = null;
              nextObtained = false;
            }

            pushSuper(psiClass);
          }

          public boolean hasNext() {
            nextElement();
            return current != null;
          }

          private void nextElement() {
            if (nextObtained) return;

            nextObtained = true;
            while (!superTypesStack.empty()) {
              assert indices.size() > 0;

              int i = indices.pop();
              PsiClassType[] superTypes = superTypesStack.peek();
              while (i < superTypes.length) {
                PsiClass clazz = superTypes[i].resolve();
                if (clazz != null && !visited.contains(clazz)) {
                  current = clazz;
                  visited.add(clazz);
                  indices.push(i + 1);
                  pushSuper(clazz);
                  return;
                }
                i++;
              }

              superTypesStack.pop();
            }

            current = null;
          }

          private void pushSuper(PsiClass clazz) {
            superTypesStack.push(clazz.getSuperTypes());
            indices.push(0);
          }

          @NotNull
          public PsiClass next() {
            nextElement();
            nextObtained = false;
            if (current == null) throw new NoSuchElementException();
            return current;
          }

          public void remove() {
            throw new IllegalStateException("should not be called");
          }
        };
      }
    };
  }

  @Nullable
  public static PsiClass getContextClass(PsiElement context) {
    while (context != null) {
      if (context instanceof GrTypeDefinition) {
        return (PsiClass)context;
      }
      else if (context instanceof GroovyFileBase) {
        return ((GroovyFileBase)context).getScriptClass();
      }

      context = context.getContext();
    }
    return null;
  }

  public static boolean mightBeLValue(GrExpression expr) {
    if (expr instanceof GrParenthesizedExpression) return mightBeLValue(((GrParenthesizedExpression)expr).getOperand());

    if (expr instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)expr;
      if (listOrMap.isMap()) return false;
      GrExpression[] initializers = listOrMap.getInitializers();
      for (GrExpression initializer : initializers) {
        if (!mightBeLValue(initializer)) return false;
      }
      return true;
    }
    if (expr instanceof GrTupleExpression) return true;
    if (expr instanceof GrReferenceExpression || expr instanceof GrIndexProperty) return true;

    if ((expr instanceof GrThisReferenceExpression || expr instanceof GrSuperReferenceExpression) &&
        GroovyConfigUtils.getInstance().isVersionAtLeast(expr, GroovyConfigUtils.GROOVY1_8)) {
      return true;
    }
    return false;
  }

  public static boolean isRawMethodCall(GrMethodCallExpression call) {
    final GroovyResolveResult result = call.advancedResolve();
    final PsiElement element = result.getElement();
    if (element == null) return false;
    if (element instanceof PsiMethod) {
      PsiType returnType = getSmartReturnType((PsiMethod)element);
      final GrExpression expression = call.getInvokedExpression();
      if (expression instanceof GrReferenceExpression && result.isInvokedOnProperty()) {
        if (returnType instanceof GrClosureType) {
          final GrClosureSignature signature = ((GrClosureType)returnType).getSignature();
          return isRawType(signature.getReturnType(), signature.getSubstitutor());
        }
      }
      else {
        return isRawType(returnType, result.getSubstitutor());
      }
    }
    if (element instanceof PsiVariable) {
      final PsiType type = call.getInvokedExpression().getType();
      if (type instanceof GrClosureType) {
        final GrClosureSignature signature = ((GrClosureType)type).getSignature();
        return isRawType(signature.getReturnType(), signature.getSubstitutor());
      }
    }

    return false;
  }

  public static boolean isRawFieldAccess(GrReferenceExpression ref) {
    PsiElement element = null;
    final GroovyResolveResult[] resolveResults = ref.multiResolve(false);
    if (resolveResults.length == 0) return false;
    final GroovyResolveResult resolveResult = resolveResults[0];
    if (resolveResult != null) {
      element = resolveResult.getElement();
    }
    if (element instanceof PsiField) {
      return isRawType(((PsiField)element).getType(), resolveResult.getSubstitutor());
    }
    else if (element instanceof GrAccessorMethod) {
      return isRawType(((GrAccessorMethod)element).getReturnType(), resolveResult.getSubstitutor());
    }
    return false;
  }

  private static boolean isRawIndexPropertyAccess(GrIndexProperty expr) {
    final GrExpression qualifier = expr.getSelectedExpression();
    final PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {

      if (InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_LIST)) {
        return com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(qualifierType, false) == null;
      }

      if (InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_MAP)) {
        return com.intellij.psi.util.PsiUtil.substituteTypeParameter(qualifierType, CommonClassNames.JAVA_UTIL_MAP, 1, false) == null;
      }
      PsiClassType classType = (PsiClassType)qualifierType;
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      GrExpression[] arguments = expr.getArgumentList().getExpressionArguments();
      PsiType[] argTypes = new PsiType[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        PsiType argType = arguments[i].getType();
        if (argType == null) argType = TypesUtil.getJavaLangObject(expr);
        argTypes[i] = argType;
      }

      MethodResolverProcessor processor = new MethodResolverProcessor("getAt", expr, false, qualifierType, argTypes, PsiType.EMPTY_ARRAY);

      final PsiClass qClass = resolveResult.getElement();
      final ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, PsiSubstitutor.EMPTY);
      if (qClass != null) {
        qClass.processDeclarations(processor, state, null, expr);
      }

      ResolveUtil.processNonCodeMethods(qualifierType, processor, expr, state);
      final GroovyResolveResult[] candidates = processor.getCandidates();
      PsiType type = null;
      if (candidates.length == 1) {
        final PsiElement element = candidates[0].getElement();
        if (element instanceof PsiMethod) {
          type = getSmartReturnType((PsiMethod)element);
        }
      }
      return isRawType(type, resolveResult.getSubstitutor());
    }
    return false;
  }

  public static boolean isRawClassMemberAccess(GrExpression expr) {
    expr = (GrExpression)skipParentheses(expr, false);

    if (expr instanceof GrMethodCallExpression) {
      return isRawMethodCall((GrMethodCallExpression)expr);
    }
    if (expr instanceof GrReferenceExpression) {
      return isRawFieldAccess((GrReferenceExpression)expr);
    }
    if (expr instanceof GrIndexProperty) {
      return isRawIndexPropertyAccess((GrIndexProperty)expr);
    }
    return false;
  }

  public static boolean isRawType(PsiType type, PsiSubstitutor substitutor) {
    if (type instanceof PsiClassType) {
      final PsiClass returnClass = ((PsiClassType)type).resolve();
      if (returnClass instanceof PsiTypeParameter) {
        final PsiTypeParameter typeParameter = (PsiTypeParameter)returnClass;
        return substitutor.substitute(typeParameter) == null;
      }
    }
    return false;
  }

  public static boolean isNewLine(PsiElement element) {
    if (element == null) return false;
    ASTNode node = element.getNode();
    if (node == null) return false;
    return node.getElementType() == GroovyTokenTypes.mNLS;
  }

  @Nullable
  public static PsiElement getPrevNonSpace(final PsiElement elem) {
    PsiElement prevSibling = elem.getPrevSibling();
    while (prevSibling instanceof PsiWhiteSpace) {
      prevSibling = prevSibling.getPrevSibling();
    }
    return prevSibling;
  }

  @Nullable
  public static PsiElement getNextNonSpace(final PsiElement elem) {
    PsiElement nextSibling = elem.getNextSibling();
    while (nextSibling instanceof PsiWhiteSpace) {
      nextSibling = nextSibling.getNextSibling();
    }
    return nextSibling;
  }

  public static PsiIdentifier getJavaNameIdentifier(GrNamedElement namedElement) {
    final PsiElement element = namedElement.getNameIdentifierGroovy();
    JavaIdentifier identifier = element.getUserData(NAME_IDENTIFIER);
    if (identifier == null) {
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (element) {
        identifier = element.getUserData(NAME_IDENTIFIER);
        if (identifier != null) {
          return identifier;
        }

        identifier = new JavaIdentifier(element.getManager(), element);
        element.putUserData(NAME_IDENTIFIER, identifier);
      }
    }
    return identifier;
  }

  @Nullable
  public static PsiElement findEnclosingStatement(@Nullable PsiElement context) {
    if (context == null) return null;
    context = PsiTreeUtil.getParentOfType(context, GrStatement.class, false);
    while (context != null) {
      final PsiElement parent = context.getParent();
      if (parent instanceof GrControlFlowOwner) return context;
      context = parent;
    }
    return null;
  }

  public static boolean isMethodCall(GrMethodCallExpression call, String methodName) {
    final GrExpression expression = call.getInvokedExpression();
    return expression instanceof GrReferenceExpression && methodName.equals(expression.getText().trim());
  }

  public static boolean hasEnclosingInstanceInScope(PsiClass clazz, PsiElement scope, boolean isSuperClassAccepted) {
    PsiElement place = scope;
    while (place != null && place != clazz && !(place instanceof PsiFile)) {
      if (place instanceof PsiClass) {
        if (isSuperClassAccepted) {
          if (InheritanceUtil.isInheritorOrSelf((PsiClass)place, clazz, true)) return true;
        }
        else {
          if (clazz.getManager().areElementsEquivalent(place, clazz)) return true;
        }
      }
      if (place instanceof PsiModifierListOwner && ((PsiModifierListOwner)place).hasModifierProperty(PsiModifier.STATIC)) return false;
      place = place.getParent();
    }
    return place == clazz;
  }

  @Nullable
  public static PsiElement skipWhitespaces(@Nullable PsiElement elem, boolean forward) {
    //noinspection ConstantConditions
    while (elem != null &&
           elem.getNode() != null &&
           TokenSets.WHITE_SPACES_OR_COMMENTS.contains(elem.getNode().getElementType())) {
      if (forward) {
        elem = elem.getNextSibling();
      }
      else {
        elem = elem.getPrevSibling();
      }
    }
    return elem;
  }

  @Nullable
  public static PsiType getSmartReturnType(PsiMethod method) {
    if (method instanceof GrMethod) {
      return ((GrMethod)method).getInferredReturnType();
    }
    else if (method instanceof GrAccessorMethod) {
      return ((GrAccessorMethod)method).getInferredReturnType();
    }
    else {
      return method.getReturnType();
    }
  }

  public static boolean isClosurePropertyGetter(PsiMethod method) {
    String methodName = method.getName();
    if (methodName.startsWith("get") && GroovyPropertyUtils.isGetterName(methodName)) { // exclude isXXX()
      PsiModifierList modifiers = method.getModifierList();

      if (!modifiers.hasModifierProperty(PsiModifier.STATIC)
          && !modifiers.hasModifierProperty(PsiModifier.PRIVATE)
          && !modifiers.hasModifierProperty(PsiModifier.PROTECTED)
          && method.getParameterList().getParametersCount() == 0) {
        final PsiType type = getSmartReturnType(method);
        if (type != null && (TypesUtil.isClassType(type, CommonClassNames.JAVA_LANG_OBJECT) || TypesUtil.isClassType(type,
                                                                                                                     GroovyCommonClassNames.GROOVY_LANG_CLOSURE))) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean isMethodUsage(PsiElement element) {
    if (element instanceof GrEnumConstant) return true;
    if (!(element instanceof GrReferenceElement || element instanceof GrThisSuperReferenceExpression)) return false;
    PsiElement parent = element.getParent();
    if (parent instanceof GrCall) {
      return true;
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      return element.equals(((GrAnonymousClassDefinition)parent).getBaseClassReferenceGroovy());
    }
    return false;
  }

  public static GroovyResolveResult[] getConstructorCandidates(GroovyPsiElement place, GroovyResolveResult[] classCandidates, PsiType[] argTypes) {
    List<GroovyResolveResult> constructorResults = new ArrayList<GroovyResolveResult>();
    for (GroovyResolveResult classResult : classCandidates) {
      final PsiElement element = classResult.getElement();
      if (element instanceof PsiClass) {
        final GroovyPsiElement context = classResult.getCurrentFileResolveContext();
        PsiClass clazz = (PsiClass)element;
        String className = clazz.getName();
        PsiType thisType =
          JavaPsiFacade.getInstance(place.getProject()).getElementFactory().createType(clazz, classResult.getSubstitutor());
        final MethodResolverProcessor processor =
          new MethodResolverProcessor(className, place, true, thisType, argTypes, PsiType.EMPTY_ARRAY);
        PsiSubstitutor substitutor = classResult.getSubstitutor();
        final ResolveState state =
          ResolveState.initial().put(PsiSubstitutor.KEY, substitutor).put(ResolverProcessor.RESOLVE_CONTEXT, context);
        final PsiMethod[] methods = clazz.getMethods();
        boolean toBreak = true;
        for (PsiMethod method : methods) {
          if (method.isConstructor() && !processor.execute(method, state)) {
            toBreak = false;
            break;
          }
        }

        NonCodeMembersContributor.runContributors(thisType, processor, place, state);
        ContainerUtil.addAll(constructorResults, processor.getCandidates());
        if (!toBreak) break;
      }
    }

    return constructorResults.toArray(new GroovyResolveResult[constructorResults.size()]);
  }

  public static boolean isAccessedForReading(GrExpression expr) {
    return !isLValue(expr);
  }

  public static boolean isAccessedForWriting(GrExpression expr) {
    if (isLValue(expr)) return true;

    PsiElement parent = PsiTreeUtil.skipParentsOfType(expr, GrParenthesizedExpression.class);

    if (parent instanceof GrUnaryExpression) {
      IElementType tokenType = ((GrUnaryExpression)parent).getOperationTokenType();
      return tokenType == GroovyTokenTypes.mINC || tokenType == GroovyTokenTypes.mDEC;
    }
    return false;
  }

  public static GrReferenceExpression qualifyMemberReference(GrReferenceExpression refExpr, PsiMember member, String name) {
    assert refExpr.getQualifierExpression() == null;

    final PsiClass clazz = member.getContainingClass();
    assert clazz != null;

    final PsiElement replaced;
    if (member.hasModifierProperty(GrModifier.STATIC)) {
      final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
        .createReferenceExpressionFromText(clazz.getQualifiedName() + "." + name);
      replaced = refExpr.replace(newRefExpr);
    }
    else {
      final PsiClass containingClass = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (member.getManager().areElementsEquivalent(containingClass, clazz)) {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText("this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
      else {
        final GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
          .createReferenceExpressionFromText(clazz.getName() + ".this." + name);
        replaced = refExpr.replace(newRefExpr);
      }
    }
    return (GrReferenceExpression)replaced;
  }

  public static GroovyResolveResult[] getConstructorCandidates(PsiClassType classType, PsiType[] argTypes, GroovyPsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    if (psiClass == null) {
      return GroovyResolveResult.EMPTY_ARRAY;
    }

    final GroovyResolveResult grResult = resolveResult instanceof GroovyResolveResult
                                         ? (GroovyResolveResult)resolveResult
                                         : new GroovyResolveResultImpl(psiClass, context, substitutor, true, true);
    return getConstructorCandidates(context, new GroovyResolveResult[]{grResult}, argTypes);
  }

  @Nullable
  public static PsiElement skipParentheses(@Nullable PsiElement element, boolean up) {
    if (element == null) return null;
    if (up) {
      PsiElement parent;
      while ((parent=element.getParent()) instanceof GrParenthesizedExpression) {
        element = parent;
      }
      return element;
    }
    else {
      while (element instanceof GrParenthesizedExpression) {
        element = ((GrParenthesizedExpression)element).getOperand();
      }
      return element;
    }
  }

  @NotNull
  public static PsiElement skipParenthesesIfSensibly(@NotNull PsiElement element, boolean up) {
    PsiElement res = skipParentheses(element, up);
    return res == null ? element : res;
  }


  @Nullable
  public static PsiElement getNamedArgumentValue(GrNamedArgument otherNamedArgument, String argumentName) {
    PsiElement parent = otherNamedArgument.getParent();

    GrNamedArgument[] arguments;
    if (parent instanceof GrArgumentList) {
      arguments = ((GrArgumentList)parent).getNamedArguments();
    }
    else {
      arguments = ((GrListOrMap)parent).getNamedArguments();
    }

    return getNamedArgumentValue(arguments, argumentName);
  }

  @Nullable
  public static PsiElement getNamedArgumentValue(GrNamedArgument[] arguments, String argumentName) {
    for (GrNamedArgument namedArgument : arguments) {
      GrArgumentLabel label = namedArgument.getLabel();
      if (label != null) {
        if (argumentName.equals(label.getName())) {
          return namedArgument.getExpression();
        }
      }
    }

    return null;
  }

  @NotNull
  public static PsiClass getOriginalClass(@NotNull PsiClass aClass) {
    PsiFile file = aClass.getContainingFile();
    if (file == null) return aClass;

    PsiFile originalFile = file.getOriginalFile();
    if (originalFile == file) return aClass;

    if (!(originalFile instanceof PsiClassOwner)) return aClass;

    String name = aClass.getName();
    if (name == null) return aClass;

    for (PsiClass originalClass : ((PsiClassOwner)originalFile).getClasses()) {
      if (name.equals(originalClass.getName())) {
        return originalClass;
      }
    }

    return aClass;
  }


  private static String[] visibilityModifiers = new String[]{GrModifier.PRIVATE, GrModifier.PROTECTED, GrModifier.PUBLIC};

  public static void escalateVisibility(PsiMember owner, PsiElement place) {
    final String visibilityModifier = VisibilityUtil.getVisibilityModifier(owner.getModifierList());
    int index;
    for (index = 0; index < visibilityModifiers.length; index++) {
      String modifier = visibilityModifiers[index];
      if (modifier.equals(visibilityModifier)) break;
    }
    for (; index < visibilityModifiers.length && !isAccessible(place, owner); index++) {
      String modifier = visibilityModifiers[index];
      owner.getModifierList().setModifierProperty(modifier, true);
    }
  }

  public static boolean isLeafElementOfType(@Nullable PsiElement element, IElementType type) {
    return element instanceof LeafElement && ((LeafElement)element).getElementType() == type;
  }

  /**
   * Returns all arguments passed to method. First argument is null if Named Arguments is present.
   */
  public static GrExpression[] getAllArguments(@NotNull GrCall call) {
    GrArgumentList argumentList = call.getArgumentList();
    if (argumentList == null) return GrExpression.EMPTY_ARRAY;

    GrClosableBlock[] closureArguments = call.getClosureArguments();
    GrExpression[] expressionArguments = argumentList.getExpressionArguments();
    GrNamedArgument[] namedArguments = argumentList.getNamedArguments();

    int length = expressionArguments.length + closureArguments.length;
    int k = 0;
    if (namedArguments.length > 0) {
      length++;
      k = 1;
    }

    GrExpression[] res = new GrExpression[length];
    for (GrExpression expressionArgument : expressionArguments) {
      res[k++] = expressionArgument;
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      res[k++] = closureArgument;
    }

    return res;
  }

  public static GrNamedArgument[] getFirstMapNamedArguments(@NotNull GrCall grCall) {
    GrNamedArgument[] res = grCall.getNamedArguments();
    if (res.length > 0) return res;

    GrExpression[] arguments = grCall.getExpressionArguments();
    if (arguments.length == 0) return GrNamedArgument.EMPTY_ARRAY;

    PsiElement firstArg = arguments[0];

    if (!(firstArg instanceof GrListOrMap)) return GrNamedArgument.EMPTY_ARRAY;

    return ((GrListOrMap)firstArg).getNamedArguments();
  }

  private static boolean seemsToBeQualifiedClassName(@Nullable GrExpression qualifier) {
    if (qualifier == null) return false;
    while (qualifier instanceof GrReferenceExpression) {
      final PsiElement nameElement = ((GrReferenceExpression)qualifier).getReferenceNameElement();
      if (((GrReferenceExpression)qualifier).getTypeArguments().length > 0) return false;
      if (nameElement == null || nameElement.getNode().getElementType() != GroovyTokenTypes.mIDENT) return false;
      IElementType dotType = ((GrReferenceExpression)qualifier).getDotTokenType();
      if (dotType != null && dotType != GroovyTokenTypes.mDOT) return false;
      qualifier = ((GrReferenceExpression)qualifier).getQualifierExpression();
    }
    return qualifier == null;
  }

  public static boolean isExpressionStatement(GrExpression expr) {
    final PsiElement parent = expr.getParent();
    if (parent instanceof GrControlFlowOwner) return true;
    if (parent instanceof GrExpression ||
        parent instanceof GrArgumentList ||
        parent instanceof GrReturnStatement ||
        parent instanceof GrAssertStatement ||
        parent instanceof GrThrowStatement ||
        parent instanceof GrSwitchStatement ||
        parent instanceof GrVariable) {
      return false;
    }
    return true;
  }

  @Nullable
  public static GrCall getMethodByNamedParameter(GrNamedArgument namedArgument) {
    PsiElement parent = namedArgument.getParent();

    PsiElement eMethodCall;

    if (parent instanceof GrArgumentList) {
      eMethodCall = parent.getParent();
    }
    else {
      if (!(parent instanceof GrListOrMap)) return null;

      PsiElement eArgumentList = parent.getParent();
      if (!(eArgumentList instanceof GrArgumentList)) return null;

      GrArgumentList argumentList = (GrArgumentList)eArgumentList;

      if (argumentList.getNamedArguments().length > 0) return null;
      if (argumentList.getExpressionArgumentIndex((GrListOrMap)parent) != 0) return null;

      eMethodCall = eArgumentList.getParent();
    }

    if (!(eMethodCall instanceof GrCall)) return null;

    return (GrCall)eMethodCall;
  }

  public static boolean isInScriptContext(GroovyPsiElement expr) {
    final GroovyPsiElement fileContext = getFileOrClassContext(expr);
    return fileContext instanceof GroovyFile && ((GroovyFile)fileContext).isScript();
  }

  public static GroovyPsiElement getFileOrClassContext(final GroovyPsiElement element) {
    GroovyPsiElement context = PsiTreeUtil.getContextOfType(element, GrTypeDefinition.class, GroovyFileBase.class);
    while (context instanceof GroovyFileBase &&
        GroovyPsiElementFactory.DUMMY_FILE_NAME.equals(FileUtil.getNameWithoutExtension(((GroovyFileBase)context).getName()))) {
      context = PsiTreeUtil.getContextOfType(context, true, GrTypeDefinition.class, GroovyFileBase.class);
    }
    return context;
  }

  public static String getAnnoAttributeValue(@NotNull PsiAnnotation annotation, final String attributeName, String defaultValue) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof GrExpression) {
      Object o = GroovyConstantExpressionEvaluator.evaluate((GrExpression)value);
      if (o instanceof String) {
        return (String)o;
      }
    }
    return defaultValue;
  }

  public static boolean getAnnoAttributeValue(@NotNull PsiAnnotation annotation, final String attributeName, boolean defaultValue) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
    if (value instanceof GrExpression) {
      Object o = GroovyConstantExpressionEvaluator.evaluate((GrExpression)value);
      if (o instanceof Boolean) {
        return (Boolean)o;
      }
    }
    return defaultValue;
  }

  public static boolean resultOfExpressionUsed(GrExpression expr) {
    while (expr.getParent() instanceof GrParenthesizedExpression) expr = (GrExpression)expr.getParent();

    final PsiElement parent = expr.getParent();
    if (parent instanceof GrExpression ||
        parent instanceof GrArgumentList ||
        parent instanceof GrReturnStatement ||
        parent instanceof GrAssertStatement ||
        parent instanceof GrThrowStatement ||
        parent instanceof GrSwitchStatement ||
        parent instanceof GrVariable) {
      return true;
    }
    return ControlFlowUtils.collectReturns(ControlFlowUtils.findControlFlowOwner(expr), true).contains(expr);
  }
}
