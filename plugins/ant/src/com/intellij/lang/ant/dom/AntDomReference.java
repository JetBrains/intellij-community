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

import org.jetbrains.annotations.Nls;

/**
 * @author Eugene Zhuravlev
 */
public interface AntDomReference {
  boolean shouldBeSkippedByAnnotator();

  void setShouldBeSkippedByAnnotator(boolean value);

  @Nls(capitalization = Nls.Capitalization.Sentence) String getUnresolvedMessagePattern();
}
