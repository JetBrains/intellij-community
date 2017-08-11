/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.callMatcher;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;

import java.util.function.Function;

/**
 * A pair of {@link CallMatcher} and a transformer function which maps a call to some new object.
 *
 * @author Tagir Valeev
 */
public class CallHandler<T> implements Function<PsiMethodCallExpression, T> {
  private final CallMatcher myMatcher;
  private final Function<PsiMethodCallExpression, T> myTransformer;

  public CallHandler(CallMatcher matcher, Function<PsiMethodCallExpression, T> transformer) {
    myMatcher = matcher;
    myTransformer = transformer;
  }

  public final CallMatcher matcher() {
    return myMatcher;
  }

  /**
   * @param call method call to transform
   * @return null if call does not pass matcher check or the result of original transformer otherwise
   */
  @Override
  public T apply(PsiMethodCallExpression call) {
    return matcher().test(call) ? myTransformer.apply(call) : null;
  }

  public T applyMethodReference(PsiMethodReferenceExpression ref) {
    return matcher().methodReferenceMatches(ref) ? myTransformer.apply(null) : null;
  }

  /**
   * Creates a new CallHandler with specific matcher and specific transformer function
   * @param matcher a matcher to be applied to the elements
   * @param transformer a transformer which accepts a method call which successfully passes matcher check
   * @param <T> a type of transformer return value
   * @return a new CallHandler
   */
  public static <T> CallHandler<T> of(CallMatcher matcher, Function<PsiMethodCallExpression, T> transformer) {
    return new CallHandler<>(matcher, transformer);
  }
}
