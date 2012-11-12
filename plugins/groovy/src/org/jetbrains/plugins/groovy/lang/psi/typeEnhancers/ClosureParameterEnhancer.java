/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;

import java.util.Map;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_IO_FILE;

/**
 * @author peter
 */
public class ClosureParameterEnhancer extends AbstractClosureParameterEnhancer {
  private final Map<String, String> simpleTypes = new HashMap<String, String>();
  private final Set<String> iterations = new HashSet<String>();

  public ClosureParameterEnhancer() {
    simpleTypes.put("times", "java.lang.Integer");
    simpleTypes.put("upto", "java.lang.Integer");
    simpleTypes.put("downto", "java.lang.Integer");
    simpleTypes.put("step", "java.lang.Integer");
    simpleTypes.put("withObjectOutputStream", "java.io.ObjectOutputStream");//todo
    simpleTypes.put("withObjectInputStream", "java.io.ObjectInputStream");
    simpleTypes.put("withOutputStream", "java.io.OutputStream");
    simpleTypes.put("withInputStream", "java.io.InputStream");
    simpleTypes.put("withDataOutputStream", "java.io.DataOutputStream");
    simpleTypes.put("withDataInputStream", "java.io.DataInputStream");
    simpleTypes.put("eachLine", "java.lang.String");
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

  }

  @Override
  @Nullable
  protected PsiType getClosureParameterType(GrClosableBlock closure, int index) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrStringInjection && index == 0) {
      return TypesUtil.createTypeByFQClassName("java.io.StringWriter", closure);
    }
    
    if (parent instanceof GrArgumentList) parent = parent.getParent();
    if (!(parent instanceof GrMethodCall)) {
      return null;
    }
    String methodName = findMethodName((GrMethodCall)parent);

    GrExpression expression = ((GrMethodCall)parent).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return null;
//    final PsiElement resolved = ((GrReferenceExpression)expression).resolve();
//    if (!(resolved instanceof GrGdkMethod)) return null;

    GrExpression qualifier = ((GrReferenceExpression)expression).getQualifierExpression();
    if (qualifier == null) return null;
    PsiType type = qualifier.getType();

    if (type == null) {
      return null;
    }

    final PsiParameter[] params = closure.getAllParameters();

    if (params.length == 1 && simpleTypes.containsKey(methodName)) {
      return TypesUtil.createTypeByFQClassName(simpleTypes.get(methodName), closure);
    }

    if (iterations.contains(methodName)) {
      if (params.length == 1) {
        return findTypeForIteration(qualifier, closure);
      }
      if (params.length == 2 && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        if (index == 0) {
          return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
        }
        return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
      }
    }
    else if (GdkMethodUtil.isWithName(methodName) && params.length == 1) {
      return type;
    }
    else if (GdkMethodUtil.EACH_WITH_INDEX.equals(methodName)) {
      PsiType res = findTypeForIteration(qualifier, closure);
      if (params.length == 2 && res != null) {
        if (index == 0) {
          return res;
        }
        return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_INTEGER, closure);
      }
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        if (params.length == 2) {
          if (index == 0) {
            return getEntryForMap(type, closure);
          }
          return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_INTEGER, closure);
        }
        if (params.length == 3) {
          if (index == 0) {
            return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
          }
          if (index == 1) {
            return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, true);
          }
          return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_INTEGER, closure);
        }
      }
    }
    else if (GdkMethodUtil.INJECT.equals(methodName) && params.length == 2) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, closure);
      }

      PsiType res = findTypeForIteration(qualifier, closure);
      if (res != null) {
        return res;
      }
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return getEntryForMap(type, closure);
      }
    }
    else if (GdkMethodUtil.EACH_PERMUTATION.equals(methodName) && params.length == 1) {
      final PsiType itemType = findTypeForIteration(qualifier, closure);
      if (itemType != null) {
        return JavaPsiFacade.getElementFactory(closure.getProject()).createTypeFromText(
          CommonClassNames.JAVA_UTIL_ARRAY_LIST + "<" + itemType.getCanonicalText() + ">", closure);
      }
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_ARRAY_LIST, closure);
    }
    else if (GdkMethodUtil.WITH_DEFAULT.equals(methodName)) {
      if (params.length == 1 && InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        return PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, true);
      }
    }
    else if (GdkMethodUtil.SORT.equals(methodName)) {
      if (params.length < 3) {
        return findTypeForIteration(qualifier, closure);
      }
    }
    else if (GdkMethodUtil.WITH_STREAM.equals(methodName)) {
      final PsiMethod method = ((GrMethodCall)parent).resolveMethod();
      if (method != null) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0) {
          return parameters[0].getType();
        }
      }
    }
    else if (GdkMethodUtil.WITH_STREAMS.equals(methodName)) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName("java.io.InputStream", closure);
      }
      else if (index == 1) return TypesUtil.createTypeByFQClassName("java.io.OutputStream", closure);
    }
    else if (GdkMethodUtil.WITH_OBJECT_STREAMS.equals(methodName)) {
      if (index == 0) {
        return TypesUtil.createTypeByFQClassName("java.io.ObjectInputStream", closure);
      }
      else if (index == 1) return TypesUtil.createTypeByFQClassName("java.io.ObjectOutputStream", closure);
    }
    return null;
  }


  @Nullable
  private static PsiType getEntryForMap(PsiType map, PsiElement context) {
    PsiType key = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 0, true);
    PsiType value = PsiUtil.substituteTypeParameter(map, CommonClassNames.JAVA_UTIL_MAP, 1, true);
    if (key != null && key != PsiType.NULL && value != null && value != PsiType.NULL) {
      return JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(
        "java.util.Map.Entry<" + key.getCanonicalText() + ", " + value.getCanonicalText() + ">", context);
    }
    return TypesUtil.createTypeByFQClassName("java.util.Map.Entry", context);
  }

  @Nullable
  public static PsiType findTypeForIteration(@NotNull GrExpression qualifier, PsiElement context) {
    PsiType iterType = qualifier.getType();
    if (iterType == null) return null;
    if (iterType instanceof PsiArrayType) {
      return TypesUtil.boxPrimitiveType(((PsiArrayType)iterType).getComponentType(), context.getManager(), context.getResolveScope());
    }
    if (iterType instanceof GrTupleType) {
      PsiType[] types = ((GrTupleType)iterType).getParameters();
      return types.length == 1 ? types[0] : null;
    }

    if (iterType instanceof GrRangeType) {
      return ((GrRangeType)iterType).getIterationType();
    }

    PsiType res = PsiUtil.extractIterableTypeParameter(iterType, true);
    if (res != null) {
      return PsiImplUtil.normalizeWildcardTypeByPosition(res, qualifier);
    }

    if (TypesUtil.isClassType(iterType, CommonClassNames.JAVA_LANG_STRING) || TypesUtil.isClassType(iterType, JAVA_IO_FILE)) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, context);
    }

    if (InheritanceUtil.isInheritor(iterType, CommonClassNames.JAVA_UTIL_MAP)) {
      return getEntryForMap(iterType, context);
    }
    return null;
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
