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

package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.Nls;

public class PatchSyntaxException extends Exception {
  private final int myLine;

  public PatchSyntaxException(int line, @Nls(capitalization = Nls.Capitalization.Sentence) String message) {
    super(message);
    myLine = line;
  }

  public int getLine() {
    return myLine;
  }

  @Override
  public String getMessage() {
    return  super.getMessage() + VcsBundle.message("patch.apply.syntax.line", myLine);
  }
}
