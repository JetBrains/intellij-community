// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Override
  public boolean checkInput(String inputString) {
    return REF_FORMAT_PATTERN.matcher(inputString).matches();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }
}
