/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.validators;

import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Checks that the specified String is a valid Git reference name.
 * See <a href="http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html">
 * http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html</a>
 */
public final class GitRefNameValidator implements InputValidator {

  private static final GitRefNameValidator INSTANCE = new GitRefNameValidator();

  // illegal control characters - from 0 to 31 and 7F (DEL)
  private static String CONTROL_CHARS;
  static {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (char c = 0; c < 32; c++) {
      sb.append(c);
    }
    sb.append('\u007F');
    sb.append("]");
    CONTROL_CHARS = sb.toString();
  }
  private static final Pattern ILLEGAL = Pattern.compile(
    "(^\\.)|" +                             // begins with a dot
    "(^-)|" +                                 // begins with '-'
    "[ ~:\\^\\?\\*\\[\\\\]+|(@\\{)+|" +     // contains invalid character: space, one of ~:^?*[\ or @{ sequence
    "(\\.\\.)+|" +                          // two dots in a row
    "(([\\./]|\\.lock)$)|" +                // ends with dot, slash or ".lock"
    CONTROL_CHARS                           // contains a control character
  );

  public static GitRefNameValidator getInstance() {
    return INSTANCE;
  }

  private GitRefNameValidator() {}

  @Override
  public boolean checkInput(String inputString) {
    return !StringUtil.isEmptyOrSpaces(inputString) && !ILLEGAL.matcher(inputString).find();
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }

  @NotNull
  public String cleanUpBranchName(@NotNull String branchName) {
    return branchName.replaceAll(ILLEGAL.pattern(), "_");
  }
}
