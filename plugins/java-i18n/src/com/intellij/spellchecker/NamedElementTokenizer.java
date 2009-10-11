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
package com.intellij.spellchecker;

import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class NamedElementTokenizer<T extends PsiNamedElement> extends Tokenizer<T> {

  @Nullable
  @Override
   public Token[] tokenize(@NotNull T element) {

    final PsiIdentifier psiIdentifier = PsiTreeUtil.getChildOfType(element, PsiIdentifier.class);
    final PsiTypeElement psiType = PsiTreeUtil.getChildOfType(element, PsiTypeElement.class);

    if (psiIdentifier == null) {
      return null;
    }

    final String identifier = psiIdentifier.getText();
    final String type = psiType==null?null:psiType.getText();

    if (identifier == null) {
      return null;
    }

    Token[] tokenFormIdentifiers = (type!=null && type.equalsIgnoreCase(identifier)) ? null : new PsiIdentifierTokenizer().tokenize(psiIdentifier);
    Token[] tokenFromType = psiType==null?null:new PsiTypeTokenizer().tokenize(psiType);

    return (tokenFromType == null && tokenFormIdentifiers == null) ? null : concat(tokenFormIdentifiers, tokenFromType);
  }

  @Nullable
  protected Token[] concat(@Nullable Token[] t1, @Nullable Token[] t2) {
    if (t1==null){
      return t2;
    }
    if (t2==null){
      return t1;
    }

    Token[] C = new Token[t1.length + t2.length];
    System.arraycopy(t1, 0, C, 0, t1.length);
    System.arraycopy(t2, 0, C, t1.length, t2.length);
    return C;
  }

}


