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
package com.intellij.vcsUtil;

import com.intellij.util.ExceptionUtil;

/**
 * @author irengrig
 *
 * @deprecated use {@link ExceptionUtil} instead
 */
public class Rethrow {
  private Rethrow() {
  }

  /**
   * @deprecated use {@link ExceptionUtil#rethrowAllAsUnchecked(Throwable)} instead
   */
  public static void reThrowRuntime(final Throwable t) {
    ExceptionUtil.rethrowAllAsUnchecked(t);
  }
}
