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
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A mutable bunch of CallHandlers which allows to dispatch a transformer call based on CallMatcher
 *
 * @author Tagir Valeev
 */
public class CallMapper<T> {
  private Map<String, List<CallHandler<T>>> myMap = new HashMap<>();

  public CallMapper() {}

  public CallMapper(CallHandler<T>... handlers) {
    for (CallHandler<T> handler : handlers) {
      register(handler);
    }
  }

  public CallMapper<T> register(CallHandler<T> handler) {
    handler.matcher().names().forEach(name -> myMap.computeIfAbsent(name, k -> new ArrayList<>()).add(handler));
    return this;
  }

  public CallMapper<T> register(CallMatcher matcher, Function<PsiMethodCallExpression, T> handler) {
    return register(CallHandler.of(matcher, handler));
  }

  public CallMapper<T> register(CallMatcher matcher, T value) {
    return register(CallHandler.of(matcher, call -> value));
  }

  public CallMapper<T> registerAll(List<CallHandler<T>> handlers) {
    handlers.forEach(this::register);
    return this;
  }

  public T mapFirst(PsiMethodCallExpression call) {
    if (call == null) return null;
    List<CallHandler<T>> functions = myMap.get(call.getMethodExpression().getReferenceName());
    if (functions == null) return null;
    for (Function<PsiMethodCallExpression, T> function : functions) {
      T t = function.apply(call);
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  public T mapFirst(PsiMethodReferenceExpression methodRef) {
    if (methodRef == null) return null;
    List<CallHandler<T>> functions = myMap.get(methodRef.getReferenceName());
    if (functions == null) return null;
    for (CallHandler<T> function : functions) {
      T t = function.applyMethodReference(methodRef);
      if (t != null) {
        return t;
      }
    }
    return null;
  }

  public Stream<T> mapAll(PsiMethodCallExpression call) {
    if (call == null) return null;
    List<CallHandler<T>> functions = myMap.get(call.getMethodExpression().getReferenceName());
    if (functions == null) return StreamEx.empty();
    return StreamEx.of(functions).map(fn -> fn.apply(call)).nonNull();
  }
}
