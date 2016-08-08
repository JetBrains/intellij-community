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
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrMultiSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignatureVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrMultiSignatureImpl implements GrMultiSignature {
  private final GrClosureSignature[] mySignatures;

  public GrMultiSignatureImpl(GrClosureSignature[] signatures) {
    mySignatures = signatures;
  }

  @Override
  public GrClosureSignature[] getAllSignatures() {
    return mySignatures;
  }

  @Override
  public boolean isValid() {
    for (GrClosureSignature signature : mySignatures) {
      if (!signature.isValid()) return false;
    }
    return true;
  }

  @Override
  public GrSignature curry(@NotNull PsiType[] args, int position, @NotNull PsiElement context) {
    List<GrClosureSignature> newClosures = new ArrayList<>();

    for (GrClosureSignature old : mySignatures) {
      final GrSignature curried = old.curry(args, position, context);
      if (curried instanceof GrClosureSignature) {
        newClosures.add((GrClosureSignature)curried);
      }
      else if (curried instanceof GrMultiSignature) {
        newClosures.addAll(Arrays.asList(((GrMultiSignature)curried).getAllSignatures()));
      }
    }
    return new GrMultiSignatureImpl(newClosures.toArray(new GrClosureSignature[newClosures.size()]));
  }

  @Override
  public void accept(@NotNull GrSignatureVisitor visitor) {
    visitor.visitMultiSignature(this);
  }
}
