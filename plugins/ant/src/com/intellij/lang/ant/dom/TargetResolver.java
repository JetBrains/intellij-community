/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 22, 2010
 */
public class TargetResolver extends PropertyProviderFinder {

  private final String myDeclaredTargetRef;
  private @Nullable AntDomTarget myContextTarget;
  private Pair<AntDomTarget, String> myResult;

  public TargetResolver(@NotNull String declaredDependencyRef, AntDomTarget contextElement) {
    super(contextElement);
    myDeclaredTargetRef = declaredDependencyRef;
    myContextTarget = contextElement;
  }

  @Nullable
  public static AntDomTarget resolve(@NotNull AntDomProject project, @Nullable AntDomTarget contextTarget, @NotNull String declaredTargetRef) {
    final TargetResolver resolver = new TargetResolver(declaredTargetRef, contextTarget);
    resolver.execute(project, null);
    final Pair<AntDomTarget, String> result = resolver.getResult();
    return result != null? result.getFirst() : null;
  }

  @Nullable
  public static Trinity<AntDomTarget, String, Map<String, AntDomTarget>> resolveWithVariants(@NotNull AntDomProject project, @Nullable AntDomTarget contextTarget, @NotNull String declaredTargetRef) {
    final TargetResolver resolver = new TargetResolver(declaredTargetRef, contextTarget);
    resolver.execute(project, null);
    final Pair<AntDomTarget, String> result = resolver.getResult();
    if (result == null) {
      return null;
    }
    return new Trinity<AntDomTarget, String, Map<String, AntDomTarget>>(result.getFirst(), result.getSecond(), resolver.getDiscoveredTargets());
  }

  protected void targetDefined(AntDomTarget target, String taregetEffectiveName, Map<String, Pair<AntDomTarget, String>> dependenciesMap) {
    if (myContextTarget != null && myResult == null && target.equals(myContextTarget)) {
      myResult = dependenciesMap.get(myDeclaredTargetRef);
      stop();
    }
  }

  protected void stageCompleted(Stage completedStage, Stage startingStage) {
    if (completedStage == Stage.RESOLVE_MAP_BUILDING_STAGE) {
      if (myResult == null) {
        final AntDomTarget target = getTargetByName(myDeclaredTargetRef);
        if (target != null) {
          myResult = new Pair<AntDomTarget, String>(target, myDeclaredTargetRef); // treat declared name as effective name
        }
      }
      stop();
    }
  }

  @Nullable
  public Pair<AntDomTarget, String> getResult() {
    return myResult;
  }

  @Override
  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
  }
}
