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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
public class MapEntryOrKeyValueHintProcessor extends SignatureHintProcessor {
  @Override
  public String getHintName() {
    return "groovy.transform.stc.MapEntryOrKeyValue";
  }

  @NotNull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@NotNull PsiMethod method,
                                                 @NotNull PsiSubstitutor substitutor,
                                                 @NotNull String[] options) {
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
      return StringUtilRt.parseInt(options[0], 0);
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
      return StringUtilRt.parseBoolean(options[0], false);
    }

    return false;
  }

  private static Boolean parseIndex(String value) {
    Couple<String> pair = parseValue(value);
    if (pair == null) return null;

    Boolean parsedValue = StringUtilRt.parseBoolean(pair.getSecond(), false);
    if ("index".equals(pair.getFirst())) {
      return parsedValue;
    }

    return null;
  }

  private static Integer parseArgNum(String value) {
    Couple<String> pair = parseValue(value);
    if (pair == null) return null;

    Integer parsedValue = StringUtilRt.parseInt(pair.getSecond(), 0);
    if ("argNum".equals(pair.getFirst())) {
      return parsedValue;
    }

    return null;
  }

  @Nullable
  private static Couple<String> parseValue(String value) {
    String[] splitted = value.split("=");

    if (splitted.length == 2) {
      return Couple.of(splitted[0].trim(), splitted[1].trim());
    }

    return null;
  }
}
