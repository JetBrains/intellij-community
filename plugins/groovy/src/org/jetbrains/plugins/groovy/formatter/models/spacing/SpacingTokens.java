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

package org.jetbrains.plugins.groovy.formatter.models.spacing;

import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

/**
 * @author ilyas
 */
public interface SpacingTokens extends GroovyElementTypes {

  TokenSet LEFT_BRACES = TokenSet.create(mLPAREN, mLBRACK, mLCURLY);
  TokenSet RIGHT_BRACES = TokenSet.create(mRPAREN, mRBRACK, mRCURLY);

  TokenSet PUNCTUATION_SIGNS = TokenSet.create(mDOT, mMEMBER_POINTER, mSPREAD_DOT, mOPTIONAL_DOT, mCOMMA, mSEMI);

  TokenSet INDEX_OR_ARRAY = TokenSet.create(PATH_INDEX_PROPERTY, ARRAY_TYPE, ARRAY_DECLARATOR);

  TokenSet PREFIXES = TokenSet.create(mDEC, mINC, mAT, mBNOT, mLNOT);
  TokenSet POSTFIXES = TokenSet.create(mDEC, mINC);
  TokenSet PREFIXES_OPTIONAL = TokenSet.create(mPLUS, mMINUS);

  TokenSet RANGES = TokenSet.create(mRANGE_EXCLUSIVE, mRANGE_INCLUSIVE);
}
