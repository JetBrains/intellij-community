/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import org.jetbrains.annotations.NotNull;

/**
 * Information about a user on GitHub.
 *
 * @author Kirill Likhodedov
 */
class GithubUser {

  enum Plan {
    FREE,
    MICRO,
    SMALL,
    MEDIUM,
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    ENTERPRISE;

    public boolean isPrivateRepoAllowed() {
      return this != FREE;
    }

    public static Plan fromString(String name) {
      for (Plan plan : values()) {
        if (plan.name().equalsIgnoreCase(name)) {
          return plan;
        }
      }
      return defaultPlan();
    }

    private static Plan defaultPlan() {
      return FREE;
    }
  }

  @NotNull private final Plan myPlan;

  GithubUser(@NotNull Plan plan) {
    myPlan = plan;
  }

  @NotNull
  Plan getPlan() {
    return myPlan;
  }

}
