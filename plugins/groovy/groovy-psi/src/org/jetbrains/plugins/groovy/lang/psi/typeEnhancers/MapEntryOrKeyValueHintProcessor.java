// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MapEntryOrKeyValueHintProcessor extends SignatureHintProcessor {
  @NlsSafe private static final String INDEX = "index";
  @NlsSafe private static final String ARG_NUM = "argNum";


  @Override
  public String getHintName() {
    return "groovy.transform.stc.MapEntryOrKeyValue";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                 @NotNull PsiSubstitutor substitutor,
                                                 String @NotNull [] options) {
    int argNum = extractArgNum(options);
    boolean index = extractIndex(options);

    PsiParameter[] parameters = method.getParameterList().getParameters();

    if (argNum >= parameters.length) return ContainerUtil.emptyList();

    PsiParameter parameter = parameters[argNum];
    PsiType type = parameter.getType();
    PsiType substituted = substitutor.substitute(type);

    if (!InheritanceUtil.isInheritor(substituted, CommonClassNames.JAVA_UTIL_MAP)) return ContainerUtil.emptyList();

    PsiType key = PsiUtil.substituteTypeParameter(substituted, CommonClassNames.JAVA_UTIL_MAP, 0, true);
    PsiType value = PsiUtil.substituteTypeParameter(substituted, CommonClassNames.JAVA_UTIL_MAP, 1, true);

    PsiClass mapEntry = JavaPsiFacade.getInstance(method.getProject()).findClass(CommonClassNames.JAVA_UTIL_MAP_ENTRY, method.getResolveScope());
    if (mapEntry == null) return ContainerUtil.emptyList();

    PsiClassType mapEntryType = JavaPsiFacade.getElementFactory(method.getProject()).createType(mapEntry, key, value);

    PsiType[] keyValueSignature = index ? new PsiType[]{key, value, PsiType.INT} : new PsiType[]{key, value};
    PsiType[] mapEntrySignature = index ? new PsiType[]{mapEntryType, PsiType.INT} : new PsiType[]{mapEntryType};

    return ContainerUtil.newArrayList(keyValueSignature, mapEntrySignature);
  }

  private static int extractArgNum(String[] options) {
    for (String value : options) {
      Integer parsedValue = parseArgNum(value);
      if (parsedValue != null) {
        return parsedValue;
      }
    }

    if (options.length == 1) {
      return StringUtil.parseInt(options[0], 0);
    }

    return 0;
  }

  private static boolean extractIndex(String[] options) {
    for (String value : options) {
      Boolean parsedValue = parseIndex(value);
      if (parsedValue != null) {
        return parsedValue;
      }
    }

    if (options.length == 1) {
      return Boolean.parseBoolean(options[0]);
    }

    return false;
  }

  private static Boolean parseIndex(String value) {
    Couple<String> pair = parseValue(value);
    if (pair == null) return null;

    if (INDEX.equals(pair.getFirst())) {
      return Boolean.valueOf(pair.getSecond());
    }

    return null;
  }

  private static Integer parseArgNum(String value) {
    Couple<String> pair = parseValue(value);
    if (pair == null) return null;

    if (ARG_NUM.equals(pair.getFirst())) {
      return StringUtil.parseInt(pair.getSecond(), 0);
    }

    return null;
  }

  @Nullable
  private static Couple<String> parseValue(String value) {
    String[] split = value.split("=");
    return split.length == 2 ? Couple.of(split[0].trim(), split[1].trim()) : null;
  }
}