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

/**
 * Git branch name validator
 */
public class GitBranchNameValidator implements InputValidator {
  static final String REGEX;

  static {
    // based on the git-check-ref-format command description
    final String goodChar = "[ -\\~&&[^\\^\\~\\:\\[\\?\\*\\.\\/]]";
    final String component = goodChar + "+(?:\\." + goodChar + ")*\\.?";
    REGEX = component + "+(?:/*" + component + ")*";
  }

  public boolean checkInput(String inputString) {
    return inputString.matches(REGEX);
  }

  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}