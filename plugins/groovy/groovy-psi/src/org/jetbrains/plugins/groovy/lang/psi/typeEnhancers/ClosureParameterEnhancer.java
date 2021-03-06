// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.CompileStaticUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;

/**
 * @author peter
 */
public class ClosureParameterEnhancer extends AbstractClosureParameterEnhancer {
  private static final Map<@NlsSafe String, @NlsSafe String> simpleTypes = new HashMap<>();
  private static final Set<@NlsSafe String> iterations = new HashSet<>();

  static {
    simpleTypes.put("times", JAVA_LANG_INTEGER);
    simpleTypes.put("upto", JAVA_LANG_INTEGER);
    simpleTypes.put("downto", JAVA_LANG_INTEGER);
    simpleTypes.put("step", JAVA_LANG_INTEGER);
    simpleTypes.put("withObjectOutputStream", "java.io.ObjectOutputStream");//todo
    simpleTypes.put("withObjectInputStream", "java.io.ObjectInputStream");
    simpleTypes.put("withOutputStream", "java.io.OutputStream");
    simpleTypes.put("withInputStream", "java.io.InputStream");
    simpleTypes.put("withDataOutputStream", "java.io.DataOutputStream");
    simpleTypes.put("withDataInputStream", "java.io.DataInputStream");
    simpleTypes.put("eachLine", JAVA_LANG_STRING);
    simpleTypes.put("eachFile", JAVA_IO_FILE);
    simpleTypes.put("eachDir", JAVA_IO_FILE);
    simpleTypes.put("eachFileRecurse", JAVA_IO_FILE);
    simpleTypes.put("traverse", JAVA_IO_FILE);
    simpleTypes.put("eachDirRecurse", JAVA_IO_FILE);
    simpleTypes.put("eachFileMatch", JAVA_IO_FILE);
    simpleTypes.put("eachDirMatch", JAVA_IO_FILE);
    simpleTypes.put("withReader", "java.io.Reader");
    simpleTypes.put("withWriter", "java.io.Writer");
    simpleTypes.put("withWriterAppend", "java.io.Writer");
    simpleTypes.put("withPrintWriter", "java.io.PrintWriter");
    simpleTypes.put("eachByte", "byte");
    simpleTypes.put("transformChar", "String");
    simpleTypes.put("transformLine", "String");
    simpleTypes.put("filterLine", "String");
    simpleTypes.put("accept", "java.net.Socket");
    simpleTypes.put("dropWhile", "java.lang.Character");
    simpleTypes.put("eachMatch", JAVA_LANG_STRING);
    simpleTypes.put("replaceAll", "java.util.regex.Matcher");
    simpleTypes.put("replaceFirst", "java.util.regex.Matcher");
    simpleTypes.put("splitEachLine", "java.util.List<java.lang.String>");
    simpleTypes.put("withBatch", "groovy.sql.BatchingStatementWrapper");

    iterations.add("each");
    iterations.add("any");
    iterations.add("every");
    iterations.add("reverseEach");
    iterations.add("collect");
    iterations.add("collectAll");
    iterations.add("collectEntries");
    iterations.add("find");
    iterations.add("findAll");
    iterations.add("retainAll");
    iterations.add("removeAll");
    iterations.add("split");
    iterations.add("groupBy");
    iterations.add("groupEntriesBy");
    iterations.add("findLastIndexOf");
    iterations.add("findIndexValues");
    iterations.add("findIndexOf");
    iterations.add("count");
    iterations.add("takeWhile");

  }

  @Override
  @Nullable
  protected PsiType getClosureParameterType(@NotNull GrFunctionalExpression closure, int index) {
    if (CompileStaticUtil.isCompileStatic(closure)) {
      return null;
    }

    return inferType(closure, index);
  }

  @Nullable
  public static PsiType inferType(@NotNull GrFunctionalExpression expression, int index) {
    PsiElement parent = expression.getParent();
    if (parent instanceof GrStringInjection && index == 0) {
      return TypesUtil.createTypeByFQClassName("java.io.StringWriter", expression);
    }

    if (parent instanceof GrArgumentList) parent = parent.getParent();
    if (!(parent instanceof GrMethodCall)) {
      return null;
    }

    String methodName = findMethodName((GrMethodCall)parent);

    GrExpression invokedExpression = ((GrMethodCall)parent).getInvokedExpression();
    if (!(invokedExpression instanceof GrReferenceExpression)) return null;

    GrExpression qualifier = ((GrReferenceExpression)invokedExpression).getQualifierExpression();
    if (qualifier == null) return null;
    PsiType type = qualifier.getType();

    if (type == null) {
      return null;
    }

    final PsiParameter[] params = expression.getAllParameters();

    if (params.length == 1 && simpleTypes.containsKey(methodName)) {

      final String typeText = simpleTypes.get(methodName);
      if (typeText.indexOf('<') < 0) {
        return TypesUtil.createTypeByFQClassName(typeText, expression);
      }
      else {
        return JavaPsiFacade.getElementFactory(expression.getProject()).createTypeFromText(typeText, expression);
      }
    }

    if (iterations.contains(methodName)) {
      if (params.length == 1) {
        return findTypeForIteration(qualifier, expression);
      }
      if (params.length == 2 && InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
        if (index == 0) {
          return PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 0, true);
        }
        return PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 1, true);
      }
    }
    else if (GdkMethodUtil.isWithName(methodName) && params.length == 1) {
      return type;
    }
    else if (GdkMethodUtil.EACH_WITH_INDEX.equals(methodName)) {
      PsiType res = findTypeForIteration(qualifier, expression);
      if (params.length == 2 && res != null) {
        if (index == 0) {
          return res;
        }
        return TypesUtil.createTypeByFQClassName(JAVA_LANG_INTEGER, expression);
      }
      if (InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
        if (params.length == 2) {
          if (index == 0) {
            return getEntryForMap(type, expression.getProject(), expression.getResolveScope());
          }
          return TypesUtil.createTypeByFQClassName(JAVA_LANG_INTEGER, expression);
        }
        if (params.length == 3) {
          if (index == 0) {
            return PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 0, true);
          }
          if (index == 1) {
            return PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 1, true);
          }
          return TypesUtil.createTypeByFQClassName(JAVA_LANG_INTEGER, expression);
        }
      }
    }
    else if (GdkMethodUtil.INJECT.equals(methodName) && params.length == 2) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName(JAVA_LANG_OBJECT, expression);
      }

      PsiType res = findTypeForIteration(qualifier, expression);
      if (res != null) {
        return res;
      }
      if (InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
        return getEntryForMap(type, expression.getProject(), expression.getResolveScope());
      }
    }
    else if (GdkMethodUtil.EACH_PERMUTATION.equals(methodName) && params.length == 1) {
      final PsiType itemType = findTypeForIteration(qualifier, expression);
      if (itemType != null) {
        return JavaPsiFacade.getElementFactory(expression.getProject()).createTypeFromText(
          JAVA_UTIL_ARRAY_LIST + "<" + itemType.getCanonicalText() + ">", expression);
      }
      return TypesUtil.createTypeByFQClassName(JAVA_UTIL_ARRAY_LIST, expression);
    }
    else if (GdkMethodUtil.WITH_DEFAULT.equals(methodName)) {
      if (params.length == 1 && InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
        return PsiUtil.substituteTypeParameter(type, JAVA_UTIL_MAP, 0, true);
      }
    }
    else if (GdkMethodUtil.SORT.equals(methodName)) {
      if (params.length < 3) {
        return findTypeForIteration(qualifier, expression);
      }
    }
    else if (GdkMethodUtil.WITH_STREAM.equals(methodName)) {
      final PsiMethod method = ((GrMethodCall)parent).resolveMethod();
      if (method instanceof GrGdkMethod) {
        return qualifier.getType();
      }
      else if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
          return parameters[0].getType();
        }
      }
    }
    else if (GdkMethodUtil.WITH_STREAMS.equals(methodName)) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName("java.io.InputStream", expression);
      }
      else if (index == 1) return TypesUtil.createTypeByFQClassName("java.io.OutputStream", expression);
    }
    else if (GdkMethodUtil.WITH_OBJECT_STREAMS.equals(methodName)) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName("java.io.ObjectInputStream", expression);
      }
      else if (index == 1) return TypesUtil.createTypeByFQClassName("java.io.ObjectOutputStream", expression);
    }
    return null;
  }

  @NotNull
  public static PsiType getEntryForMap(@Nullable PsiType map, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    PsiType key = PsiUtil.substituteTypeParameter(map, JAVA_UTIL_MAP, 0, true);
    PsiType value = PsiUtil.substituteTypeParameter(map, JAVA_UTIL_MAP, 1, true);

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiClass entryClass = JavaPsiFacade.getInstance(project).findClass(JAVA_UTIL_MAP_ENTRY, scope);
    if (entryClass == null) {
      if (key != null && key != PsiType.NULL && value != null && value != PsiType.NULL) {
        final String text = String.format("%s<%s,%s>", JAVA_UTIL_MAP_ENTRY, key.getCanonicalText(), value.getCanonicalText());
        return factory.createTypeFromText(text, null);
      }
      else {
        return factory.createTypeByFQClassName(JAVA_UTIL_MAP_ENTRY, scope);
      }
    }
    else {
      return factory.createType(entryClass, key, value);
    }
  }

  @Nullable
  public static PsiType findTypeForIteration(@NotNull GrExpression qualifier, @NotNull PsiElement context) {
    PsiType iterType = qualifier.getType();
    if (iterType == null) return null;

    final PsiType type = findTypeForIteration(iterType, context);
    if (type == null) return null;

    return PsiImplUtil.normalizeWildcardTypeByPosition(type, qualifier);
  }

  @Contract("null,_ -> null")
  @Nullable
  public static PsiType findTypeForIteration(@Nullable PsiType type, @NotNull PsiElement context) {
    final PsiManager manager = context.getManager();
    final GlobalSearchScope resolveScope = context.getResolveScope();

    if (type instanceof PsiArrayType) {
      return TypesUtil.boxPrimitiveType(((PsiArrayType)type).getComponentType(), manager, resolveScope);
    }
    if (type instanceof GrTupleType) {
      PsiType[] types = ((GrTupleType)type).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    if (type instanceof GrRangeType) {
      return ((GrRangeType)type).getIterationType();
    }

    PsiType fromIterator = findTypeFromIteratorMethod(type, context);
    if (fromIterator != null) {
      return fromIterator;
    }

    PsiType extracted = PsiUtil.extractIterableTypeParameter(type, true);
    if (extracted != null) {
      return extracted;
    }

    if (TypesUtil.isClassType(type, JAVA_LANG_STRING, JAVA_IO_FILE)) {
      return PsiType.getJavaLangString(manager, resolveScope);
    }

    if (InheritanceUtil.isInheritor(type, JAVA_UTIL_MAP)) {
      return getEntryForMap(type, manager.getProject(), resolveScope);
    }
    return type;
  }

  @Nullable
  private static PsiType findTypeFromIteratorMethod(@Nullable PsiType type, PsiElement context) {
    if (!(type instanceof PsiClassType)) return null;

    final GroovyResolveResult[] candidates = ResolveUtil.getMethodCandidates(type, "iterator", context, PsiType.EMPTY_ARRAY);
    final GroovyResolveResult candidate = PsiImplUtil.extractUniqueResult(candidates);
    final PsiElement element = candidate.getElement();
    if (!(element instanceof PsiMethod)) return null;

    final PsiType returnType = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.getSmartReturnType((PsiMethod)element);
    final PsiType iteratorType = candidate.getSubstitutor().substitute(returnType);

    return PsiUtil.substituteTypeParameter(iteratorType, JAVA_UTIL_ITERATOR, 0, false);
  }

  @Nullable
  private static String findMethodName(@NotNull GrMethodCall methodCall) {
    GrExpression expression = methodCall.getInvokedExpression();
    if (expression instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)expression).getReferenceName();
    }
    return null;
  }
}
