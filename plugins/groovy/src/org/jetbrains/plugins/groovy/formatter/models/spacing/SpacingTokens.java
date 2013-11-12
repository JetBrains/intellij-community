/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @author ilyas
 */
public interface SpacingTokens extends GroovyElementTypes {

  TokenSet LEFT_BRACES = TokenSet.create(mLPAREN, mLBRACK, mLCURLY);
  TokenSet RIGHT_BRACES = TokenSet.create(mRPAREN, mRBRACK, mRCURLY);

  TokenSet INDEX_OR_ARRAY = TokenSet.create(PATH_INDEX_PROPERTY, ARRAY_TYPE, ARRAY_DECLARATOR);

  TokenSet PREFIXES = TokenSet.create(mDEC, mINC, mAT, mBNOT, mLNOT);
  TokenSet POSTFIXES = TokenSet.create(mDEC, mINC);
  TokenSet PREFIXES_OPTIONAL = TokenSet.create(mPLUS, mMINUS);

  TokenSet RANGES = TokenSet.create(mRANGE_EXCLUSIVE, mRANGE_INCLUSIVE);

  TokenSet LOGICAL_OPERATORS = TokenSet.create(mLAND, mLOR);
  TokenSet EQUALITY_OPERATORS = TokenSet.create(mEQUAL, mNOT_EQUAL);
  TokenSet RELATIONAL_OPERATORS = TokenSet.create(mGT, mGE, mLT, mLE);
  TokenSet BITWISE_OPERATORS = TokenSet.create(mBAND, mBOR, mBXOR);
  TokenSet ADDITIVE_OPERATORS = TokenSet.create(mPLUS, mMINUS);
  TokenSet MULTIPLICATIVE_OPERATORS = TokenSet.create(mSTAR, mDIV, mMOD);
  TokenSet SHIFT_OPERATORS = TokenSet.create(COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN);
  TokenSet REGEX_OPERATORS = TokenSet.create(mREGEX_FIND, mREGEX_MATCH);
}
