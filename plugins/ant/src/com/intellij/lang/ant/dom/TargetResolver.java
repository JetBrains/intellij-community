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
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public class TargetResolver extends PropertyProviderFinder {

  private List<String> myDeclaredTargetRefs;
  private @Nullable AntDomTarget myContextTarget;

  private Result myResult;

  public static class Result {
    private String myRefsString;
    private Map<String, Pair<AntDomTarget, String>> myMap = new HashMap<>(); // declared target name -> pair[target, effective name]
    private Map<String, AntDomTarget> myVariants;

    void add(String declaredTargetRef, Pair<AntDomTarget, String> pair) {
      myMap.put(declaredTargetRef, pair);
    }

    void setVariants(Map<String, AntDomTarget> variants) {
      myVariants = variants;
    }

    public String getRefsString() {
      return myRefsString;
    }

    public void setRefsString(String refsString) {
      myRefsString = refsString;
    }

    @NotNull
    public Collection<String> getTargetReferences() {
      return Collections.unmodifiableSet(myMap.keySet());
    }

    @Nullable
    public Pair<AntDomTarget, String> getResolvedTarget(String declaredTargetRef) {
      return myMap.get(declaredTargetRef);
    }

    @NotNull
    public Map<String, AntDomTarget> getVariants() {
      return myVariants != null? myVariants : Collections.emptyMap();
    }
  }

  private TargetResolver(@NotNull Collection<String> declaredDependencyRefs, @Nullable AntDomTarget contextElement) {
    super(contextElement);
    myResult = new Result();
    myDeclaredTargetRefs = new ArrayList<>(declaredDependencyRefs);
    myContextTarget = contextElement;
  }

  @NotNull
  public static Result resolve(@NotNull AntDomProject project, @Nullable AntDomTarget contextTarget, @NotNull String declaredTargetRef) {
    return resolve(project, contextTarget, Arrays.asList(declaredTargetRef));
  }

  public static Result resolve(AntDomProject project, AntDomTarget contextTarget, @NotNull Collection<String> declaredTargetRefs) {
    final TargetResolver resolver = new TargetResolver(declaredTargetRefs, contextTarget);
    resolver.execute(project, null);
    final Result result = resolver.getResult();
    result.setVariants(resolver.getDiscoveredTargets());
    return result;
  }
  
  public interface TargetSink {
    void duplicateTargetDetected(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String targetEffectiveName);
  }
  
  public static void validateDuplicateTargets(AntDomProject project, final TargetSink sink) {
    final TargetResolver resolver = new TargetResolver(Collections.emptyList(), null) {
      protected void duplicateTargetFound(AntDomTarget existingTarget, AntDomTarget duplicatingTarget, String taregetEffectiveName) {
        sink.duplicateTargetDetected(existingTarget, duplicatingTarget, taregetEffectiveName);
      }

      protected void stageCompleted(Stage completedStage, Stage startingStage) {
        if (Stage.RESOLVE_MAP_BUILDING_STAGE.equals(completedStage)) {
          stop();
        }
      }
    };
    resolver.execute(project, null);
  }

  protected void targetDefined(AntDomTarget target, String targetEffectiveName, Map<String, Pair<AntDomTarget, String>> dependenciesMap) {
    if (myContextTarget != null && myDeclaredTargetRefs.size() > 0 && target.equals(myContextTarget)) {
      for (Iterator<String> it = myDeclaredTargetRefs.iterator(); it.hasNext();) {
        final String declaredRef = it.next();
        final Pair<AntDomTarget, String> result = dependenciesMap.get(declaredRef);
        if (result != null) {
          myResult.add(declaredRef, result);
          it.remove();
        }
      }
      stop();
    }
  }

  protected void stageCompleted(Stage completedStage, Stage startingStage) {
    if (completedStage == Stage.RESOLVE_MAP_BUILDING_STAGE) {
      if (myDeclaredTargetRefs.size() > 0) {
        for (Iterator<String> it = myDeclaredTargetRefs.iterator(); it.hasNext();) {
          final String declaredRef = it.next();
          final AntDomTarget result = getTargetByName(declaredRef);
          if (result != null) {
            myResult.add(declaredRef, Pair.create(result, declaredRef)); // treat declared name as effective name
            it.remove();
          }
        }
      }
      stop();
    }
  }

  @NotNull
  public Result getResult() {
    return myResult;
  }

  @Override
  protected void propertyProviderFound(PropertiesProvider propertiesProvider) {
  }
}
