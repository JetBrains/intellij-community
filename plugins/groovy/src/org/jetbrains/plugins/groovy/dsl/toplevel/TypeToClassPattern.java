/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.ObjectPattern;
import com.intellij.psi.PsiClassType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class TypeToClassPattern extends ObjectPattern<Object, TypeToClassPattern> {
  private final ElementPattern myPattern;

  public TypeToClassPattern(ElementPattern pattern) {
    super(Object.class);
    myPattern = pattern;
  }

  @Override
  public boolean accepts(@Nullable Object o, ProcessingContext context) {
    if (o instanceof Pair) {
      o = ((Pair)o).first;
    }
    if (o instanceof PsiClassType) {
      o = ((PsiClassType)o).resolve();
    }
    if (o != null) {
      return myPattern.accepts(o);
    }
    return false;
  }

  public static TypeToClassPattern create(ElementPattern pattern) {
    return new TypeToClassPattern(pattern);
  }
}
