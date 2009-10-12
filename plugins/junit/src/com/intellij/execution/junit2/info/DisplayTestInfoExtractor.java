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

package com.intellij.execution.junit2.info;

public interface DisplayTestInfoExtractor {
  String getComment(PsiClassLocator classLocator);
  String getName(PsiClassLocator classLocator);

  DisplayTestInfoExtractor FOR_CLASS = new DisplayTestInfoExtractor() {
    public String getComment(final PsiClassLocator classLocator) {
      return classLocator.getPackage();
    }

    public String getName(final PsiClassLocator classLocator) {
      return classLocator.getName();
    }
  };

  DisplayTestInfoExtractor CLASS_FULL_NAME = new DisplayTestInfoExtractor() {
    public String getComment(final PsiClassLocator classLocator) {
      return classLocator.getQualifiedName();
    }

    public String getName(final PsiClassLocator classLocator) {
      return null;
    }
  };
}
