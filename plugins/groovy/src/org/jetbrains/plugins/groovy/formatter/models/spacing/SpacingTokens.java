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

package org.jetbrains.plugins.groovy.formatter.models.spacing;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @author ilyas
 */
public interface SpacingTokens {

  TokenSet LEFT_BRACES = TokenSet.create(GroovyTokenTypes.mLPAREN, GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mLCURLY);
  TokenSet RIGHT_BRACES = TokenSet.create(GroovyTokenTypes.mRPAREN, GroovyTokenTypes.mRBRACK, GroovyTokenTypes.mRCURLY);

  TokenSet INDEX_OR_ARRAY = TokenSet.create(GroovyElementTypes.PATH_INDEX_PROPERTY, GroovyElementTypes.ARRAY_TYPE,
                                            GroovyElementTypes.ARRAY_DECLARATOR);

  TokenSet PREFIXES = TokenSet.create(GroovyTokenTypes.mDEC, GroovyTokenTypes.mINC, GroovyTokenTypes.mAT, GroovyTokenTypes.mBNOT,
                                      GroovyTokenTypes.mLNOT);
  TokenSet POSTFIXES = TokenSet.create(GroovyTokenTypes.mDEC, GroovyTokenTypes.mINC);
  TokenSet PREFIXES_OPTIONAL = TokenSet.create(GroovyTokenTypes.mPLUS, GroovyTokenTypes.mMINUS);

  TokenSet RANGES = TokenSet.create(GroovyTokenTypes.mRANGE_EXCLUSIVE, GroovyTokenTypes.mRANGE_INCLUSIVE);

  TokenSet LOGICAL_OPERATORS = TokenSet.create(GroovyTokenTypes.mLAND, GroovyTokenTypes.mLOR);
  TokenSet EQUALITY_OPERATORS = TokenSet.create(GroovyTokenTypes.mEQUAL, GroovyTokenTypes.mNOT_EQUAL);
  TokenSet RELATIONAL_OPERATORS = TokenSet.create(GroovyTokenTypes.mGT, GroovyTokenTypes.mGE, GroovyTokenTypes.mLT, GroovyTokenTypes.mLE,
                                                  GroovyTokenTypes.mCOMPARE_TO);
  TokenSet BITWISE_OPERATORS = TokenSet.create(GroovyTokenTypes.mBAND, GroovyTokenTypes.mBOR, GroovyTokenTypes.mBXOR);
  TokenSet ADDITIVE_OPERATORS = TokenSet.create(GroovyTokenTypes.mPLUS, GroovyTokenTypes.mMINUS);
  TokenSet MULTIPLICATIVE_OPERATORS = TokenSet.create(GroovyTokenTypes.mSTAR, GroovyTokenTypes.mDIV, GroovyTokenTypes.mMOD);
  TokenSet SHIFT_OPERATORS = TokenSet.create(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, GroovyElementTypes.COMPOSITE_RSHIFT_SIGN,
                                             GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN);
  TokenSet REGEX_OPERATORS = TokenSet.create(GroovyTokenTypes.mREGEX_FIND, GroovyTokenTypes.mREGEX_MATCH);
}
