/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.util;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.struct.StructMember;
import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.attr.StructLocalVariableTableAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.java.decompiler.main.DecompilerContext.CURRENT_METHOD_WRAPPER;
import static org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute.ATTRIBUTE_LOCAL_VARIABLE_TABLE;

/**
 * @author Alexandru-Constantin Bledea
 * @since March 07, 2016
 */
public final class StructUtils {

  private StructUtils() {
  }

  /**
   * @return the local variables of the current method
   */
  public static List<String> getCurrentMethodLocalVariableNames() {
    final MethodWrapper method = (MethodWrapper) DecompilerContext.getProperty(CURRENT_METHOD_WRAPPER);
    if (null == method) {
      return Collections.emptyList();
    }
    return getLocalVariables(method.methodStruct);
  }

  /**
   * @param structMember the struct member from which to extract the local variables
   * @return the local variables of the struct member
   */
  public static List<String> getLocalVariables(final StructMember structMember) {
    final VBStyleCollection<StructGeneralAttribute, String> methodStruct = structMember.getAttributes();
    final StructGeneralAttribute generalAttribute = methodStruct.getWithKey(ATTRIBUTE_LOCAL_VARIABLE_TABLE);
    if (generalAttribute instanceof StructLocalVariableTableAttribute) {
      final StructLocalVariableTableAttribute table = (StructLocalVariableTableAttribute) generalAttribute;
      return Collections.unmodifiableList(new ArrayList<>(table.getMapVarNames().values()));
    }
    return Collections.emptyList();
  }

}
