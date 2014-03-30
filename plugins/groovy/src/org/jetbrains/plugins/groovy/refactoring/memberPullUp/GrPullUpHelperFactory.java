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
package org.jetbrains.plugins.groovy.refactoring.memberPullUp;

import com.intellij.refactoring.memberPullUp.PullUpData;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.memberPullUp.PullUpHelperFactory;

/**
 * Created by Max Medvedev on 10/4/13
 */
public class GrPullUpHelperFactory implements PullUpHelperFactory{
  @Override
  public PullUpHelper createPullUpHelper(PullUpData data) {
    return new GrPullUpHelper(data);
  }
}
