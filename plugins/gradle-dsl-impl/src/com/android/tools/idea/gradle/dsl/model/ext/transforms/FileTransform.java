/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FileTransform extends SingleArgumentMethodTransform {
  public FileTransform() {
    super(PropertyUtil.FILE_METHOD_NAME, PropertyUtil.FILE_CONSTRUCTOR_NAME);
  }

  @Nullable
  @Override
  public GradleDslElement transform(@Nullable GradleDslElement e) {
    if (e == null) {
      return null;
    }

    GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
    if (methodCall.getArguments().size() == 1) {
      return methodCall.getArguments().get(0);
    }
    else if (methodCall.isConstructor() && methodCall.getMethodName().equals(PropertyUtil.FILE_CONSTRUCTOR_NAME)) {
      return new FakeFileElement(e.getParent(), methodCall);
    }

    return null;
  }

  @NotNull
  @Override
  public GradleDslExpression replace(@NotNull GradleDslElement holder,
                                     @Nullable GradleDslElement oldElement,
                                     @NotNull GradleDslExpression newElement,
                                     @NotNull String name) {
    // Make sure we don't try and replace a FakeElement, instead we delete the old methodcall and create a new one.
    if (oldElement != null && transform(oldElement) instanceof FakeElement) {
      PropertyUtil.removeElement(oldElement);
      oldElement = null;
    }
    return super.replace(holder, oldElement, newElement, name);
  }
}
