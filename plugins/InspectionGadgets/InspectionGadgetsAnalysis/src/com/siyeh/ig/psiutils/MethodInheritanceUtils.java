/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.codeInspection.reference.RefMethod;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class MethodInheritanceUtils {

  private MethodInheritanceUtils() {}

  public static Set<RefMethod> calculateSiblingMethods(RefMethod method) {
    final Set<RefMethod> siblingMethods = new HashSet<>();
    final Stack<RefMethod> pendingMethods = new Stack<>();
    pendingMethods.add(method);
    while (!pendingMethods.isEmpty()) {
      final RefMethod methodToAnalyze = pendingMethods.pop();
      siblingMethods.add(methodToAnalyze);
      final Collection<RefMethod> overridingMethods = methodToAnalyze.getDerivedMethods();
      for (RefMethod overridingMethod : overridingMethods) {
        if (!siblingMethods.contains(overridingMethod) &&
            !pendingMethods.contains(overridingMethod)) {
          pendingMethods.add(overridingMethod);
        }
      }
      final Collection<RefMethod> superMethods = methodToAnalyze.getSuperMethods();
      for (RefMethod superMethod : superMethods) {
        if (!siblingMethods.contains(superMethod) &&
            !pendingMethods.contains(superMethod)) {
          pendingMethods.add(superMethod);
        }
      }
    }
    return siblingMethods;
  }
}
