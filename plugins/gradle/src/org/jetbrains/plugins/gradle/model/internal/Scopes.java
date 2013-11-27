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
package org.jetbrains.plugins.gradle.model.internal;

import org.jetbrains.plugins.gradle.model.GradleDependencyScope;

/**
 * @author Vladislav.Soroka
 * @since 11/25/13
 */
public class Scopes {
  private boolean myForProductionCompile;
  private boolean myForProductionRuntime;
  private boolean myForTestCompile;
  private boolean myForTestRuntime;
  private boolean myIsProvided;

  public Scopes(GradleDependencyScope scope) {
    myForProductionCompile = scope.isForProductionCompile();
    myForProductionRuntime = scope.isForProductionRuntime();
    myForTestCompile = scope.isForTestCompile();
    myForTestRuntime = scope.isForTestRuntime();
    myIsProvided = scope == GradleDependencyScope.PROVIDED_COMPILE || scope == GradleDependencyScope.PROVIDED_RUNTIME;
  }

  public GradleDependencyScope[] getScopes() {
    if (myIsProvided) {
      if (myForProductionCompile && myForProductionRuntime && myForTestCompile && myForTestRuntime) {
        return new GradleDependencyScope[]{GradleDependencyScope.PROVIDED_COMPILE};
      }
      else if (!myForProductionCompile && myForProductionRuntime && !myForTestCompile && myForTestRuntime) {
        return new GradleDependencyScope[]{GradleDependencyScope.PROVIDED_RUNTIME};
      }
      else if (!myForProductionCompile && myForProductionRuntime && myForTestCompile && myForTestRuntime) {
        return new GradleDependencyScope[]{GradleDependencyScope.TEST_COMPILE, GradleDependencyScope.PROVIDED_RUNTIME};
      }
    }

    if (myForProductionCompile && myForProductionRuntime && myForTestCompile && myForTestRuntime) {
      return new GradleDependencyScope[]{GradleDependencyScope.COMPILE};
    }
    else if (!myForProductionCompile && myForProductionRuntime && !myForTestCompile && myForTestRuntime) {
      return new GradleDependencyScope[]{GradleDependencyScope.RUNTIME};
    }
    else if (!myForProductionCompile && !myForProductionRuntime && myForTestCompile && myForTestRuntime) {
      return new GradleDependencyScope[]{GradleDependencyScope.TEST_COMPILE};
    }
    else if (!myForProductionCompile && !myForProductionRuntime && !myForTestCompile && myForTestRuntime) {
      return new GradleDependencyScope[]{GradleDependencyScope.TEST_RUNTIME};
    }
    else if (!myForProductionCompile && myForProductionRuntime && myForTestCompile && myForTestRuntime) {
      return new GradleDependencyScope[]{GradleDependencyScope.TEST_COMPILE, GradleDependencyScope.RUNTIME};
    }
    else {
      return new GradleDependencyScope[]{GradleDependencyScope.COMPILE};
    }
  }

  public void add(GradleDependencyScope scope) {
    myForProductionCompile = myForProductionCompile || scope.isForProductionCompile();
    myForProductionRuntime = myForProductionRuntime || scope.isForProductionRuntime();
    myForTestCompile = myForTestCompile || scope.isForTestCompile();
    myForTestRuntime = myForTestRuntime || scope.isForTestRuntime();
    myIsProvided = myIsProvided || scope == GradleDependencyScope.PROVIDED_COMPILE || scope == GradleDependencyScope.PROVIDED_RUNTIME;
  }
}
