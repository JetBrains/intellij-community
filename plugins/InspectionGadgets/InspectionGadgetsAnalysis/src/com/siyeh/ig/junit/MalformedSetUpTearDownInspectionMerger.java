/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.codeInspection.ex.InspectionElementsMerger;

/**
 * @author Bas Leijdekkers
 */
public class MalformedSetUpTearDownInspectionMerger extends InspectionElementsMerger {

  @Override
  public String getMergedToolName() {
    return "MalformedSetUpTearDown";
  }

  @Override
  public String[] getSourceToolNames() {
    return new String[] { "TeardownIsPublicVoidNoArg", "SetupIsPublicVoidNoArg" };
  }

  @Override
  public String[] getSuppressIds() {
    return new String[] { "TearDownWithIncorrectSignature", "SetUpWithIncorrectSignature" };
  }
}
