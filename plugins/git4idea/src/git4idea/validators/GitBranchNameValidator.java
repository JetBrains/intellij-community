package git4idea.validators;
/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.ui.InputValidator;

import java.util.regex.Pattern;

/**
 * Git branch name validator. The validation rule is based on what is described
 * git-check-ref-format(1) command description.
 */
public class GitBranchNameValidator implements InputValidator {
  /**
   * the regular expression that
   */
  private static final Pattern REGEX;

  static {
    // based on the git-check-ref-format command description
    final String goodChar = "[\\!-\\~&&[^\\^\\~\\:\\[\\?\\*\\.\\/]]";
    final String component = "(?:" + goodChar + "+\\.?)+";
    REGEX = Pattern.compile(component + "+(?:/*" + component + ")*");
  }

  /**
   * The class is stateless, so the static instance is the safe to use.
   */
  public static final GitBranchNameValidator INSTANCE = new GitBranchNameValidator();

  /**
   * {@inheritDoc}
   */
  public boolean checkInput(String inputString) {
    return REGEX.matcher(inputString).matches();
  }

  /**
   * {@inheritDoc}
   */
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}