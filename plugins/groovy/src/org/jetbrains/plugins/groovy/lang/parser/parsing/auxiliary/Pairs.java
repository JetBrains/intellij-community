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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import com.intellij.psi.tree.IElementType;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */
public abstract class Pairs {
  public static Map<IElementType, IElementType> pairElementsMap = new HashMap<IElementType, IElementType>();

  static {
    pairElementsMap.put(mLPAREN, mRPAREN);
    pairElementsMap.put(mRPAREN, mLPAREN);

    pairElementsMap.put(mLBRACK, mRBRACK);
    pairElementsMap.put(mRBRACK, mLBRACK);

    pairElementsMap.put(mLCURLY, mRCURLY);
    pairElementsMap.put(mRCURLY, mLCURLY);

    pairElementsMap.put(mGSTRING_SINGLE_BEGIN, mGSTRING_SINGLE_END);
    pairElementsMap.put(mGSTRING_SINGLE_END, mGSTRING_SINGLE_BEGIN);
  }
}
