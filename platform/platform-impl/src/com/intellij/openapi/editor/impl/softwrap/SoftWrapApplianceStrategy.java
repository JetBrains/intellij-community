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
package com.intellij.openapi.editor.impl.softwrap;

/**
 * Strategy interface for ruling if soft wraps should be processed.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 *
 * @author Denis Zhdanov
 * @since 10/6/10 8:08 AM
 */
public interface SoftWrapApplianceStrategy {

  /**
   * Allows to answer if soft wraps should be processed.
   *
   * @return    <code>true</code> if soft wraps should be processed; <code>false</code> otherwise
   */
  boolean processSoftWraps();
}
