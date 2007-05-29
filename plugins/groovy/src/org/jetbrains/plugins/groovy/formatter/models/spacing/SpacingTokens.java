/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.psi.tree.TokenSet;

/**
 * @author ilyas
 */
public abstract class SpacingTokens implements GroovyElementTypes {

  protected final static TokenSet LEFT_BRACES = TokenSet.create(mLPAREN, mLBRACK, mLCURLY);
  protected final static TokenSet RIGHT_BRACES = TokenSet.create(mRPAREN, mRBRACK, mRCURLY);

  protected final static TokenSet PUNCTUATION_SIGNS = TokenSet.create(mDOT,
      mMEMBER_POINTER,
      mSPREAD_DOT,
      mOPTIONAL_DOT,
      mCOMMA,
      mSEMI);

  protected final static TokenSet DOTS = TokenSet.create(mDOT,
      mMEMBER_POINTER,
      mSPREAD_DOT,
      mOPTIONAL_DOT);

  protected final static TokenSet METHOD_OR_CALL = TokenSet.create(METHOD_DEFINITION,
      PATH_METHOD_CALL,
      NEW_EXPRESSION,
      CONSTRUCTOR_DEFINITION);
  protected final static TokenSet INDEX_OR_ARRAY = TokenSet.create(PATH_INDEX_PROPERTY, ARRAY_TYPE, ARRAY_DECLARATOR);
  protected final static TokenSet THIS_OR_SUPER = TokenSet.create(kTHIS, kSUPER);

  protected final static TokenSet PREFIXES = TokenSet.create(mDEC, mINC, mAT, mBNOT, mLNOT);
  protected final static TokenSet POSTFIXES = TokenSet.create(mDEC, mINC);
  protected final static TokenSet PREFIXES_OPTIONAL = TokenSet.create(mPLUS, mMINUS);

  protected final static TokenSet RANGES = TokenSet.create(mRANGE_EXCLUSIVE, mRANGE_INCLUSIVE);

  protected final static TokenSet BLOCKS = TokenSet.create(OPEN_BLOCK,
      CONSTRUCTOR_BODY,
      CLASS_BLOCK,
      ENUM_BLOCK,
      INTERFACE_BLOCK,
      ANNOTATION_BLOCK);

}
