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
import git4idea.config.GitRefNameValidatorSettings;
import git4idea.config.GitRefNameValidatorSettingsInterface;
import git4idea.util.MethodChainer;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks that the specified String is a valid Git reference name.
 * See <a href="http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html">
 * http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html</a>
 */
public final class GitRefNameValidator implements InputValidator {

  private static final GitRefNameValidator INSTANCE = new GitRefNameValidator();
  @NotNull
  private static final GitRefNameValidatorSettingsInterface applicationSettings = GitRefNameValidatorSettings.getInstance();

  // illegal control characters - from 0 to 31 and 7F (DEL)
  private static final String CONTROL_CHARS;
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
  private static final Pattern ILLEGAL_CHARS = Pattern.compile(
    "(^\\.)|" +                             // begins with a dot
    "(^-)|" +                               // begins with '-'
    "(^/)|" +                               // begins with '/'
    "(\\.\\.)+|" +                          // two dots in a row
    "[ ~:^?*\\[\\\\]+|(@\\{)+|" +           // contains invalid character: space, one of ~:^?*[\ or @{ sequence
    CONTROL_CHARS                           // contains a control character
  );

  private static final Pattern ILLEGAL = Pattern.compile(
    "(([./]|\\.lock)$)|" +                  // ends with dot, slash or ".lock"
    "\\.(?=/)|" +                           // has a dot before slash in the middle
    "(?<=/)\\.|" +                          // has a dot after slash in the middle
    ILLEGAL_CHARS.pattern()
  );

  public static GitRefNameValidator getInstance() {
    return INSTANCE;
  }

  private GitRefNameValidator() {}

  @NotNull
  private static String getReplacementString() {
    return applicationSettings.getReplacementOption().getReplacementString();
  }

  @NotNull
  private static String removeDoubleQuotes(String branchName) {
    return branchName.replaceAll("\"", "");
  }

  @NotNull
  private static String replaceIllegalChars(String branchName) {
    return branchName.replaceAll(ILLEGAL_CHARS.pattern(), getReplacementString());
  }

  @NotNull
  private static String replaceIllegal(String branchName) {
      return branchName.replaceAll(ILLEGAL.pattern(), getReplacementString());
  }

  @NotNull
  private static String deduplicateForwardSlashes(String branchName) {
    return branchName.replaceAll("(/){2,}", "/");
  }

  @NotNull
  private static String deduplicateUnderscores(String branchName) {
    int myMax = applicationSettings.getMaxNumberOfConsecutiveUnderscores();

    String underscorePattern = String.format("(_){%s,}", myMax + 1);
    String replacementString = "_".repeat(myMax);

    return branchName.replaceAll(underscorePattern, replacementString);
  }

  @NotNull
  private static String convertToLowerCase(@NotNull String branchName) {
    return applicationSettings.isConvertingToLowerCase() ? branchName.toLowerCase(Locale.getDefault()) : branchName;
  }

  public static boolean isOn() {
    return applicationSettings.isOn();
  }

  public boolean checkInput(String inputString) {
    return !StringUtil.isEmptyOrSpaces(inputString) && !ILLEGAL.matcher(inputString).find();
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }

  @NotNull
  public String cleanUpBranchName(@NotNull String branchName) {
    if (!isOn()) {
      return branchName;
    }

    return MethodChainer.wrap(branchName)
      .run(GitRefNameValidator::replaceIllegal)
      .run(GitRefNameValidator::removeDoubleQuotes)
      .run(GitRefNameValidator::deduplicateForwardSlashes)
      .run(GitRefNameValidator::deduplicateUnderscores)
      .run(GitRefNameValidator::convertToLowerCase)
      .unwrap();
  }

  @NotNull
  public String cleanUpBranchNameOnTyping(@NotNull String branchName) {
    if (!isOn()) {
      return branchName;
    }

    return MethodChainer.wrap(branchName)
      .run(GitRefNameValidator::replaceIllegalChars)
      .run(GitRefNameValidator::convertToLowerCase)
      .unwrap();
  }
}
