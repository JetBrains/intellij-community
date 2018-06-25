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
package git4idea.validators;

import com.intellij.openapi.ui.InputValidator;

import java.util.regex.Pattern;

/**
 * Git branch name validator. The validation rule is based on what is described
 * git-check-ref-format(1) command description.  
 * 
 * @deprecated Use {@link GitRefNameValidator}.
 */
@Deprecated
public class GitBranchNameValidator implements InputValidator {
  /**
   * the regular expression that checks branch name
   */
  private static final Pattern REF_FORMAT_PATTERN;

  static {
    // based on the git-check-ref-format command description
    final String goodChar = "[!-~&&[^\\^~:\\[\\]\\?\\*\\./<>\\|'`]]";
    final String component = "(?:" + goodChar + "+\\.?)+";
    REF_FORMAT_PATTERN = Pattern.compile("^" + component + "+(?:/*" + component + ")*$");
  }

  /**
   * The class is stateless, so the static instance is the safe to use.
   */
  public static final GitBranchNameValidator INSTANCE = new GitBranchNameValidator();

  /**
   * {@inheritDoc}
   */
  public boolean checkInput(String inputString) {
    return REF_FORMAT_PATTERN.matcher(inputString).matches();
  }

  /**
   * {@inheritDoc}
   */
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}
