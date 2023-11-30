// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.validators;

import com.intellij.openapi.options.advanced.AdvancedSettings;
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

  private static final Pattern REPLACE = Pattern.compile(
    " +|" +                           // space
    CONTROL_CHARS
  );

  private static final Pattern DROP = Pattern.compile(
    "(^\\.)|" +                             // begins with a dot
    "(^-)|" +                               // begins with '-'
    "(^/)|" +                               // begins with '/'
    "[~:^?*\"\\[\\\\]+|(@\\{)+|" +          // invalid character: one of ~:^?*"[\ or @{ sequence
    "/(?=/)|" +                             // has a double slash
    "(\\.(?=\\.))+|" +                      // two dots in a row
    "\\.(?=/)|" +                           // has a dot before slash in the middle
    "(?<=/)\\.|"                            // has a dot after slash in the middle
  );

  private static final Pattern ENDPATTERNS = Pattern.compile(
    "(([./]|\\.lock)$)|"                    // ends with dot, slash or ".lock"
  );

  private static final Pattern VALIDATEPATTERNS = Pattern.compile(
    DROP.pattern() +
    ENDPATTERNS.pattern() +
    REPLACE.pattern()
  );

  public static GitRefNameValidator getInstance() {
    return INSTANCE;
  }

  private GitRefNameValidator() {}

  @Override
  public boolean checkInput(String inputString) {
    return !StringUtil.isEmptyOrSpaces(inputString) && !VALIDATEPATTERNS.matcher(inputString).find();
  }

  @Override
  public boolean canClose(String inputString) {
    return checkInput(inputString);
  }

  public @NotNull String cleanUpBranchName(@NotNull String branchName) {
    return cleanUpBranchNameOnTyping(branchName).replaceAll(ENDPATTERNS.pattern(), "");
  }

  // On typing replace only space, drop other invalid chars.
  public @NotNull String cleanUpBranchNameOnTyping(@NotNull String branchName) {
    return branchName.replaceAll(DROP.pattern(), "").replaceAll(REPLACE.pattern(), AdvancedSettings.getString("git.branch.cleanup.symbol"));
  }
}
